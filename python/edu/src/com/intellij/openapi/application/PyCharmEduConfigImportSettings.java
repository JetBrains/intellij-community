package com.intellij.openapi.application;

import com.intellij.util.ThreeState;

// see com.intellij.openapi.application.ConfigImportHelper.getConfigImportSettings
@SuppressWarnings("UnusedDeclaration")
public class PyCharmEduConfigImportSettings extends ConfigImportSettings {

  @Override
  protected String getProductName(ThreeState full) {
    return "WebStorm";
  }

  @Override
  public String getCustomPathsSelector() {
    return "WebIDE10";
  }

  @Override
  public String getExecutableName() {
    return "webstorm";
  }

  @Override
  public String[] getMainJarNames() {
    return new String[] { "webide", "webstorm"};
  }
}
