// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareModuleConfiguratorImpl;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ReSTService extends PyDefaultProjectAwareService<
  ReSTService.ServiceState, ReSTService, ReSTService.AppService, ReSTService.ModuleService> {

  private static final PyDefaultProjectAwareServiceClasses<ServiceState, ReSTService, AppService, ModuleService> SERVICE_CLASSES =
    new PyDefaultProjectAwareServiceClasses<>(AppService.class, ModuleService.class);


  protected ReSTService() {
    super(new ServiceState());
  }


  public final void setWorkdir(String workDir) {
    getState().DOC_DIR = workDir;
  }

  public static ReSTService getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }


  @NotNull
  public static PyDefaultProjectAwareServiceModuleConfigurator getConfigurator() {
    return new PyDefaultProjectAwareModuleConfiguratorImpl<>(SERVICE_CLASSES);
  }

  public final String getWorkdir() {
    return getState().DOC_DIR;
  }

  public final boolean txtIsRst() {
    return getState().TXT_IS_RST;
  }

  public final void setTxtIsRst(boolean isRst) {
    getState().TXT_IS_RST = isRst;
  }

  public static final class ServiceState {
    public String DOC_DIR = "";
    public boolean TXT_IS_RST = false;
  }

  @State(name = "AppReSTService", storages = @Storage(value = "ReSTService.xml", roamingType = RoamingType.DISABLED))
  public static final class AppService extends ReSTService {
  }

  @State(name = "ReSTService")
  public static final class ModuleService extends ReSTService {
  }
}
