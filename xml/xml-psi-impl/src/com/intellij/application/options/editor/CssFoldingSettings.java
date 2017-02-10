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
package com.intellij.application.options.editor;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: zolotov
 * Date: 4/18/13
 * @deprecated use {@link XmlFoldingSettings}
 * todo: delete after 2017.1 release 
 */
@State(
  name="CssFoldingSettings",
  storages= {
    @Storage(value = "editor.codeinsight.xml", deprecated = true)}
)
public class CssFoldingSettings implements PersistentStateComponent<CssFoldingSettings> {
  public static CssFoldingSettings getInstance() {
    return ServiceManager.getService(CssFoldingSettings.class);
  }

  private boolean myCollapseDataUri = true;

  public boolean isCollapseDataUri() {
    return myCollapseDataUri;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setCollapseDataUri(boolean value) {
    myCollapseDataUri = value;
  }

  @Nullable
  @Override
  public CssFoldingSettings getState() {
    return this;
  }

  @Override
  public void loadState(CssFoldingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
