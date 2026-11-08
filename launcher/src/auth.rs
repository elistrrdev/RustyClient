use std::sync::Mutex;

use base64::Engine;
use serde::Deserialize;
use serde_json::{Value, json};
use tokio::sync::oneshot;

use crate::config::Account;
use crate::state::{Core, core, now_secs, runtime};
use crate::win;

pub const CANCELLED: &str = "cancelled";

const CLIENT_ID: &str = "00000000402b5328";
const REDIRECT_URI: &str = "https://login.live.com/oauth20_desktop.srf";
const SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";

static LOGIN_TX: Mutex<Option<oneshot::Sender<Result<String, String>>>> = Mutex::new(None);

pub fn authorize_url() -> String {
    let mut url = url::Url::parse("https://login.live.com/oauth20_authorize.srf").unwrap();
    url.query_pairs_mut()
        .append_pair("client_id", CLIENT_ID)
        .append_pair("response_type", "code")
        .append_pair("scope", SCOPE)
        .append_pair("redirect_uri", REDIRECT_URI)
        .append_pair("prompt", "select_account");
    url.to_string()
}

pub fn parse_redirect(address: &str) -> Option<Result<String, String>> {
    if !address.starts_with(REDIRECT_URI) {
        return None;
    }
    let parsed = url::Url::parse(address).ok()?;
    let mut code = None;
    let mut error = None;
    let mut description = None;
    for (key, value) in parsed.query_pairs() {
        match key.as_ref() {
            "code" => code = Some(value.to_string()),
            "error" => error = Some(value.to_string()),
            "error_description" => description = Some(value.to_string()),
            _ => {}
        }
    }
    if let Some(code) = code {
        return Some(Ok(code));
    }
    if error.as_deref() == Some("access_denied") {
        return Some(Err(CANCELLED.into()));
    }
    if error.is_some() || description.is_some() {
        return Some(Err(description.or(error).unwrap_or_default()));
    }
    None
}

pub fn deliver(result: Result<String, String>) {
    if let Some(tx) = LOGIN_TX.lock().unwrap().take() {
        let _ = tx.send(result);
    }
}

pub fn start_login() {
    let core = core().clone();
    let (tx, rx) = oneshot::channel();
    if let Some(old) = LOGIN_TX.lock().unwrap().replace(tx) {
        let _ = old.send(Err(CANCELLED.into()));
    }
    core.set_auth("waiting", "Finish signing in with the Microsoft window.");
    win::post(win::WM_APP_LOGIN_OPEN);
    runtime().spawn(async move {
        let Ok(result) = rx.await else {
            return;
        };
        match result {
            Ok(code) => {
                core.set_auth("working", "Signing you in...");
                win::post(win::WM_APP_RESTORE);
                match login_with_code(&core, &code).await {
                    Ok(account) => {
                        let name = account.name.clone();
                        {
                            let mut store = core.accounts.lock().unwrap();
                            store.upsert(account);
                            store.save();
                        }
                        core.set_auth("done", format!("Signed in as {name}"));
                    }
                    Err(message) => core.set_auth("error", message),
                }
            }
            Err(message) if message == CANCELLED => core.set_auth("idle", ""),
            Err(message) => core.set_auth("error", message),
        }
    });
}

pub fn cancel_login() {
    deliver(Err(CANCELLED.into()));
    win::post(win::WM_APP_LOGIN_CLOSE);
}

#[derive(Deserialize)]
struct MsaTokens {
    access_token: String,
    refresh_token: String,
}

async fn msa_token(core: &Core, params: &[(&str, &str)]) -> Result<MsaTokens, String> {
    let mut form = vec![
        ("client_id", CLIENT_ID),
        ("redirect_uri", REDIRECT_URI),
        ("scope", SCOPE),
    ];
    form.extend_from_slice(params);
    let response = core
        .http
        .post("https://login.live.com/oauth20_token.srf")
        .form(&form)
        .send()
        .await
        .map_err(network_error)?;
    if !response.status().is_success() {
        return Err("Microsoft rejected the sign-in. Please sign in again.".into());
    }
    response.json::<MsaTokens>().await.map_err(|_| "Microsoft sent an unexpected response.".to_string())
}

async fn login_with_code(core: &Core, code: &str) -> Result<Account, String> {
    let tokens = msa_token(core, &[("grant_type", "authorization_code"), ("code", code)]).await?;
    finish(core, tokens).await
}

async fn refresh(core: &Core, account: &Account) -> Result<Account, String> {
    let tokens = msa_token(
        core,
        &[("grant_type", "refresh_token"), ("refresh_token", account.msa_refresh.as_str())],
    )
    .await?;
    finish(core, tokens).await
}

fn network_error(_: reqwest::Error) -> String {
    "Could not reach the sign-in servers. Check your internet connection.".into()
}

async fn finish(core: &Core, msa: MsaTokens) -> Result<Account, String> {
    let xbl: Value = core
        .http
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .header("Accept", "application/json")
        .json(&json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": msa.access_token,
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT",
        }))
        .send()
        .await
        .map_err(network_error)?
        .json()
        .await
        .map_err(|_| "Xbox Live sign-in failed.".to_string())?;
    let xbl_token = xbl["Token"].as_str().ok_or("Xbox Live sign-in failed.")?.to_string();

    let response = core
        .http
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .header("Accept", "application/json")
        .json(&json!({
            "Properties": {
                "SandboxId": "RETAIL",
                "UserTokens": [xbl_token],
            },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT",
        }))
        .send()
        .await
        .map_err(network_error)?;
    let status = response.status();
    let xsts: Value = response.json().await.unwrap_or(Value::Null);
    if !status.is_success() {
        return Err(match xsts["XErr"].as_u64().unwrap_or(0) {
            2148916233 => "This Microsoft account has no Xbox profile yet. Create one at xbox.com and try again.".into(),
            2148916235 => "Xbox Live is not available in your country or region.".into(),
            2148916236 | 2148916237 => "This account needs adult verification on xbox.com first.".into(),
            2148916238 => "This is a child account. An adult has to add it to a Microsoft family first.".into(),
            _ => "Xbox Live refused the sign-in.".into(),
        });
    }
    let xsts_token = xsts["Token"].as_str().ok_or("Xbox Live sign-in failed.")?;
    let uhs = xsts["DisplayClaims"]["xui"][0]["uhs"].as_str().ok_or("Xbox Live sign-in failed.")?;

    let mc: Value = core
        .http
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") }))
        .send()
        .await
        .map_err(network_error)?
        .json()
        .await
        .map_err(|_| "Minecraft services rejected the sign-in.".to_string())?;
    let mc_token = mc["access_token"]
        .as_str()
        .ok_or("Minecraft services rejected the sign-in.")?
        .to_string();
    let expires_in = mc["expires_in"].as_u64().unwrap_or(86400);

    let response = core
        .http
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&mc_token)
        .send()
        .await
        .map_err(network_error)?;
    if response.status().as_u16() == 404 {
        return Err("This account does not own Minecraft: Java Edition.".into());
    }
    let profile: Value = response
        .json()
        .await
        .map_err(|_| "Could not load your Minecraft profile.".to_string())?;
    let uuid = profile["id"].as_str().ok_or("Could not load your Minecraft profile.")?.to_string();
    let name = profile["name"].as_str().unwrap_or("Player").to_string();
    let skin_url = profile["skins"]
        .as_array()
        .and_then(|skins| {
            skins
                .iter()
                .find(|s| s["state"].as_str() == Some("ACTIVE"))
                .or_else(|| skins.first())
        })
        .and_then(|s| s["url"].as_str())
        .unwrap_or_default()
        .replace("http://", "https://");

    Ok(Account {
        uuid,
        name,
        skin_url,
        xuid: jwt_claim(&mc_token, "xuid").unwrap_or_default(),
        mc_token,
        mc_expires: now_secs() + expires_in,
        msa_refresh: msa.refresh_token,
    })
}

fn jwt_claim(token: &str, claim: &str) -> Option<String> {
    let payload = token.split('.').nth(1)?;
    let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(payload.trim_end_matches('='))
        .ok()?;
    let value: Value = serde_json::from_slice(&bytes).ok()?;
    match &value[claim] {
        Value::String(s) => Some(s.clone()),
        Value::Number(n) => Some(n.to_string()),
        _ => None,
    }
}

pub async fn active_account(core: &Core) -> Result<Account, String> {
    let _guard = core.refresh_lock.lock().await;
    let account = core
        .accounts
        .lock()
        .unwrap()
        .active()
        .cloned()
        .ok_or("Sign in with your Microsoft account to play.")?;
    if account.mc_expires > now_secs() + 600 {
        return Ok(account);
    }
    match refresh(core, &account).await {
        Ok(fresh) => {
            let mut store = core.accounts.lock().unwrap();
            store.upsert(fresh.clone());
            store.save();
            Ok(fresh)
        }
        Err(_) => Err(format!("The session for {} expired. Please sign in again.", account.name)),
    }
}

pub fn refresh_profiles_in_background() {
    runtime().spawn(async {
        let core = core();
        let accounts = core.accounts.lock().unwrap().accounts.clone();
        for account in accounts {
            if account.mc_expires > now_secs() + 3600 {
                continue;
            }
            if let Ok(fresh) = refresh(core, &account).await {
                let mut store = core.accounts.lock().unwrap();
                let active = store.active.clone();
                store.upsert(fresh);
                store.active = active;
                store.save();
            }
        }
    });
}
