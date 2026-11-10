# Third party assets

## Outfit

Copyright 2021 The Outfit Project Authors. Licensed under the SIL Open Font License 1.1.
Bundled as `launcher/ui/fonts/outfit.woff2`.
The in-game client bundles static instances `client/src/main/resources/assets/rustyclient/font/outfit-medium.ttf`, `outfit-semibold.ttf` and `outfit-bold.ttf` (weights 500, 600 and 700), made with the fontTools instancer from the Google Fonts variable font.

## Lucide icons

Copyright (c) Lucide Contributors. Licensed under the ISC License.
Icon paths are bundled in `launcher/ui/icons.js` and, converted from `lucide-static` 0.460.0, in `client/src/main/resources/assets/rustyclient/icons.txt`.

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

## Dependency logos

The About page of the launcher shows the logos of the software it depends on, in `launcher/ui/brands/`. They are trademarks of their owners and are only used to name that software. Rusty Client is not affiliated with or endorsed by any of them.

- Chromium (`chromium.svg`): The Chromium Authors, from Wikimedia Commons (`Chromium_Logo.svg`).
- Rust (`rust.svg`): the Rust logo from rust-lang.org, owned by the Rust Foundation and published under CC-BY 4.0, recolored to a single light color.
- Java (`java.svg`): Oracle, the SVG is from devicon (MIT License, https://github.com/devicons/devicon).
- Minecraft (`minecraft.png`): Mojang Studios, the `pack.png` from the Minecraft client.
- Fabric (`fabric.png`): FabricMC, the icon from the Fabric Loader jar (Apache License 2.0).
- Windows (`windows10.svg`, `windows11.svg`): Microsoft, the SVGs are from devicon (MIT License).

## Optimization mods

The launcher downloads these Fabric mods from Modrinth into every game instance and keeps them updated, the same way it handles Fabric API. They are not part of `RustyClient.exe`, `RustyClient-Setup.exe` or this repository, and no code from them is used in Rusty Client.

- Sodium by CaffeineMC, PolyForm Shield License 1.0.0 (https://modrinth.com/mod/sodium).
- Lithium by CaffeineMC, LGPL-3.0-only (https://modrinth.com/mod/lithium).
- FerriteCore by malte0811, MIT License (https://modrinth.com/mod/ferrite-core).
- ImmediatelyFast by RaphiMC, LGPL-3.0-or-later (https://modrinth.com/mod/immediatelyfast).
- EntityCulling by tr7zw, tr7zw Protective License (https://modrinth.com/mod/entityculling).
- Fabric API by FabricMC, Apache License 2.0 (https://modrinth.com/mod/fabric-api).

## Chromium Embedded Framework

Copyright (c) 2008-2026 Marshall A. Greenblatt. Licensed under the BSD 3-Clause License. The CEF runtime files are not part of `RustyClient.exe` or `RustyClient-Setup.exe`. They are downloaded once from the official CEF binary distribution (`cef-builds.spotifycdn.com`, the minimal Windows 64 bit archive) and checked against its sha1.

## AppleSkin

The Saturation module offers the feature set of AppleSkin by squeek502 (food values in tooltips, saturation and exhaustion on the HUD, hunger, saturation and health restore previews, debug screen info). It is an independent implementation, no AppleSkin code or textures are bundled.

## armor-hud

Copyright (c) 2021 BerdinskiyBear. Licensed under the MIT License. The Armor Status module adapts its slot rendering, anchors and options (https://github.com/uku3lig/armor-hud).

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## PlayerHealthIndicators

Copyright (c) 2022 Gaider10. Licensed under the MIT License. The Health Indicator module adapts its heart layout above players (https://github.com/Gaider10/PlayerHealthIndicators).

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Build tools

Gradle and Fabric Loom build the in-game client. They are build tools only and nothing from them is bundled.
