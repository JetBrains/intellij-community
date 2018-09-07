// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
@State(name = "TaskSettings", storages = @Storage("tasks.xml"))
public class TaskSettings implements PersistentStateComponent<TaskSettings> {
  public boolean ALWAYS_DISPLAY_COMBO = false;
  public int CONNECTION_TIMEOUT = 5000;

  public static TaskSettings getInstance() {
    return ServiceManager.getService(TaskSettings.class);
  }

  @Override
  public TaskSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull TaskSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}