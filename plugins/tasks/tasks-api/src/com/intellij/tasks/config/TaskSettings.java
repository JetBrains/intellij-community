/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tasks.config;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Dmitry Avdeev
 *         Date: 3/23/12
 */
@State(
  name = "TaskSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
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
  public void loadState(TaskSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
