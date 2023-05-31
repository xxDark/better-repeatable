package dev.xdark.betterrepeatable;

public abstract class BetterRepeatableExtension {
	private boolean usePluginAsDependency = true;

	/**
	 * @return Whether to include Gradle plugin as compileOnly dependency.<br>
	 * If set to {@code true}, {@code Repeatable} annotation will be visible
	 * without need to manually include API dependency.<br>
	 * Enabled by default.
	 */
	public boolean isUsePluginAsDependency() {
		return usePluginAsDependency;
	}

	/**
	 * @param usePluginAsDependency Whether Gradle plugin should be used as compileOnly dependency.
	 */
	public void setUsePluginAsDependency(boolean usePluginAsDependency) {
		this.usePluginAsDependency = usePluginAsDependency;
	}
}
