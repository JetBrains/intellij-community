// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.defaultProjectAwareService;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

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


  protected PyDefaultProjectAwareService(@NotNull STATE defaultState) {
    myCurrentState = defaultState;
  }

  @Override
  public @NotNull STATE getState() {
    return myCurrentState;
  }

  @Override
  public void loadState(@NotNull STATE state) {
    XmlSerializerUtil.copyBean(state, myCurrentState);
  }
}
