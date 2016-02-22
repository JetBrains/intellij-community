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
package com.jetbrains.python;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
@State(name = "ReSTService")
public class ReSTService implements PersistentStateComponent<ReSTService> {
  public String DOC_DIR = "";
  public boolean TXT_IS_RST = false;

  public ReSTService() {
  }

  @Override
  public ReSTService getState() {
    return this;
  }

  @Override
  public void loadState(ReSTService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void setWorkdir(String workDir) {
    DOC_DIR = workDir;
  }

  public static ReSTService getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, ReSTService.class);
  }

  public String getWorkdir() {
    return DOC_DIR;
  }

  public boolean txtIsRst() {
    return TXT_IS_RST;
  }

  public void setTxtIsRst(boolean isRst) {
    TXT_IS_RST = isRst;
  }
}
