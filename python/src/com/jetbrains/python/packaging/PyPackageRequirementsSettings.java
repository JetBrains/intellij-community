// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
@ApiStatus.Internal

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

  public final @NotNull String getRequirementsPath() {
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

  public static @NotNull PyPackageRequirementsSettings getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }

  public static @NotNull PyDefaultProjectAwareServiceModuleConfigurator getConfigurator() {
    return new PyDefaultProjectAwareModuleConfiguratorImpl<>(SERVICE_CLASSES);
  }

  public static final class ServiceState {
    @OptionTag("requirementsPath") public @NotNull String myRequirementsPath = DEFAULT_REQUIREMENTS_PATH;

    @OptionTag("versionSpecifier") public @NotNull PyRequirementsVersionSpecifierType myVersionSpecifier = PyRequirementsVersionSpecifierType.COMPATIBLE;

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
