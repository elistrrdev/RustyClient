package com.rustyclient.core;

public enum Category {
	HUD("HUD"),
	VISUAL("Visual"),
	UTILITY("Utility"),
	COMBAT("Combat");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
