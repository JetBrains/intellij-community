// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.defaultProjectAwareService;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.CustomImlComponentService;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param <STATE> class with service state
 * @param <SERVICE> service itself
 * @param <APP_SERVICE> service inheritor that uses {@link com.intellij.openapi.components.State} annotation to store state on app level
 * @param <MODULE_SERVICE> same for module level
 */
public abstract class PyDefaultProjectAwareService<
  STATE,
  SERVICE extends PyDefaultProjectAwareService<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE>,
  APP_SERVICE extends SERVICE,
  MODULE_SERVICE extends SERVICE> implements PersistentStateComponent<STATE> {

  private final @NotNull STATE myCurrentState;

  protected final @Nullable String myModuleComponentName;
  protected final @Nullable Module myModule;
  protected final @Nullable Class<STATE> myStateClass;

  protected PyDefaultProjectAwareService(@NotNull STATE defaultState) {
    myCurrentState = defaultState;
    myModuleComponentName = null;
    myModule = null;
    myStateClass = null;
  }

  protected PyDefaultProjectAwareService(@NotNull STATE defaultState, @NotNull String componentName, @NotNull Class<STATE> stateClass, @NotNull Module module) {
    myCurrentState = defaultState;
    myModuleComponentName = componentName;
    myModule = module;
    myStateClass = stateClass;
  }

  @Override
  public @NotNull STATE getState() {
    if (myModule == null) {
      return myCurrentState;
    }
    var componentService = CustomImlComponentService.getInstance(myModule.getProject());
    STATE value = componentService.getComponentValue(myModule, myModuleComponentName, myStateClass);
    return value != null ? value : myCurrentState;
  }

  @Override
  public void loadState(@NotNull STATE state) {
    if (myModule == null) {
      XmlSerializerUtil.copyBean(state, myCurrentState);
      return;
    }

    var componentService = CustomImlComponentService.getInstance(myModule.getProject());
    WriteAction.run(() -> {
      componentService.setComponentValueBlocking(myModule, myModuleComponentName, state);
    });

  }
}
