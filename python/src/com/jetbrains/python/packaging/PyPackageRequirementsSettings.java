/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareModuleConfiguratorImpl;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyPackageRequirementsSettings extends PyDefaultProjectAwareService<
  PyPackageRequirementsSettings.ServiceState,
  PyPackageRequirementsSettings,
  PyPackageRequirementsSettings.AppService,
  PyPackageRequirementsSettings.ModuleService> {

  private static final PyDefaultProjectAwareServiceClasses<
        ServiceState,
        PyPackageRequirementsSettings,
        AppService,
        ModuleService> SERVICE_CLASSES = new PyDefaultProjectAwareServiceClasses<>(AppService.class, ModuleService.class);
  private static final String DEFAULT_REQUIREMENTS_PATH = "requirements.txt";

  protected PyPackageRequirementsSettings() {
    super(new ServiceState());
  }

  @NotNull
  public final String getRequirementsPath() {
    return getState().myRequirementsPath;
  }

  public void setRequirementsPath(@NotNull String path) {
    getState().myRequirementsPath = path;
  }

  public boolean getSpecifyVersion() {
    return getState().myVersionSpecifier != PyRequirementsVersionSpecifierType.NO_VERSION;
  }

  public final PyRequirementsVersionSpecifierType getVersionSpecifier() {
    return getState().myVersionSpecifier;
  }

  public final void setVersionSpecifier(PyRequirementsVersionSpecifierType versionSpecifier) {
    getState().myVersionSpecifier = versionSpecifier;
  }

  public final boolean getRemoveUnused() {
    return getState().myRemoveUnused;
  }

  public final boolean setRemoveUnused(boolean removeUnused) {
    return getState().myRemoveUnused = removeUnused;
  }

  public final boolean getModifyBaseFiles() {
    return getState().myModifyBaseFiles;
  }

  public final boolean setModifyBaseFiles(boolean modifyBaseFiles) {
    return getState().myModifyBaseFiles = modifyBaseFiles;
  }

  public final boolean getKeepMatchingSpecifier() {
    return getState().myKeepMatchingSpecifier;
  }

  public final void setKeepMatchingSpecifier(boolean forceUpdateVersionSpecifier) {
    getState().myKeepMatchingSpecifier = forceUpdateVersionSpecifier;
  }

  public final boolean isDefaultPath() {
    return getRequirementsPath().equals(DEFAULT_REQUIREMENTS_PATH);
  }

  @NotNull
  public static PyPackageRequirementsSettings getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }

  @NotNull
  public static PyDefaultProjectAwareServiceModuleConfigurator getConfigurator() {
    return new PyDefaultProjectAwareModuleConfiguratorImpl<>(SERVICE_CLASSES);
  }

  public static final class ServiceState {
    @NotNull
    @OptionTag("requirementsPath")
    public String myRequirementsPath = DEFAULT_REQUIREMENTS_PATH;

    @NotNull
    @OptionTag("versionSpecifier")
    public PyRequirementsVersionSpecifierType myVersionSpecifier = PyRequirementsVersionSpecifierType.COMPATIBLE;

    @OptionTag("removeUnused")
    public boolean myRemoveUnused = false;

    @OptionTag("modifyBaseFiles")
    public boolean myModifyBaseFiles = false;

    @OptionTag("keepMatchingSpecifier")
    public boolean myKeepMatchingSpecifier = true;
  }

  @State(name = "AppPackageRequirementsSettings", storages = @Storage(value = "PackageRequirementsSettings.xml", roamingType = RoamingType.DISABLED))
  public static final class AppService extends PyPackageRequirementsSettings {

  }

  @State(name = "PackageRequirementsSettings")
  public static final class ModuleService extends PyPackageRequirementsSettings {
  }
}
