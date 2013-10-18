package com.jetbrains.python.packaging;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
@State(name = "PackageRequirementsSettings",
       storages = {@Storage(file = "$MODULE_FILE$")})
public class PyPackageRequirementsSettings implements PersistentStateComponent<PyPackageRequirementsSettings> {
  public static final String DEFAULT_REQUIREMENTS_PATH = "requirements.txt";

  @NotNull
  private String myRequirementsPath = DEFAULT_REQUIREMENTS_PATH;

  @NotNull
  @Override
  public PyPackageRequirementsSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyPackageRequirementsSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public String getRequirementsPath() {
    return myRequirementsPath;
  }

  public void setRequirementsPath(@NotNull String path) {
    myRequirementsPath = path;
  }

  @NotNull
  public static PyPackageRequirementsSettings getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, PyPackageRequirementsSettings.class);
  }
}
