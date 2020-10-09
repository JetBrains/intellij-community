package de.plushnikov.intellij.plugin.settings;

public class LombokPluginState {
  private String pluginVersion = "";
  private boolean enableRuntimePatch = false;

  public String getPluginVersion() {
    return pluginVersion;
  }

  public void setPluginVersion(String pluginVersion) {
    this.pluginVersion = pluginVersion;
  }

  public boolean isEnableRuntimePatch() {
    return enableRuntimePatch;
  }

  public void setEnableRuntimePatch(boolean enableRuntimePatch) {
    this.enableRuntimePatch = enableRuntimePatch;
  }
}
