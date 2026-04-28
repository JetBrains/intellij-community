// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.defaultProjectAwareService;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Storage for service app and module level inheritors
 */
public final class PyDefaultProjectAwareServiceClasses<
  STATE,
  SERVICE extends PyDefaultProjectAwareService<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE>,
  APP_SERVICE extends SERVICE,
  MODULE_SERVICE extends SERVICE,
  MODULE_SERVICE_FACTORY extends PyModuleServiceFactory<MODULE_SERVICE>> {

  private final @NotNull Class<APP_SERVICE> myAppServiceClass;

  private final @NotNull Class<MODULE_SERVICE_FACTORY> myServiceFactory;

  public PyDefaultProjectAwareServiceClasses(@NotNull Class<APP_SERVICE> appServiceClass,
                                             @NotNull Class<MODULE_SERVICE_FACTORY> serviceFactory) {
    myAppServiceClass = appServiceClass;
    myServiceFactory = serviceFactory;
  }
  /**
   * Use it for "getInstance" function. Returns module-level if module is set, app level otherwise
   */
  public SERVICE getService(@Nullable Module module) {
    if (module == null || !module.canStoreSettings()) {
      return getAppService();
    }
    return getModuleService(module);
  }

  public @NotNull APP_SERVICE getAppService() {
    return ApplicationManager.getApplication().getService(myAppServiceClass);
  }

  public MODULE_SERVICE getModuleService(@NotNull Module module) {
    return module.getProject().getService(myServiceFactory).getService(module);
  }

  @ApiStatus.Internal
  public void copyFromAppToModule(@NotNull Module module) {
    getModuleService(module).loadState(getAppService().getState());
  }
}
