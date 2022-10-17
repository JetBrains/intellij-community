/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DomTableView extends AbstractTableView<DomElement> {
  private final List<DataProvider> myCustomDataProviders = new SmartList<>();

  public DomTableView(final Project project) {
    super(project);
  }

  public DomTableView(final Project project, final @Nls String emptyPaneText, final String helpID) {
    super(project, emptyPaneText, helpID);
  }

  public void addCustomDataProvider(@NotNull DataProvider provider) {
    myCustomDataProviders.add(provider);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    for (DataProvider provider : myCustomDataProviders) {
      Object data = provider.getData(dataId);
      if (data != null) return data;
    }
    return super.getData(dataId);
  }

  @Override
  protected void wrapValueSetting(@NotNull final DomElement domElement, final Runnable valueSetter) {
    if (domElement.isValid()) {
      WriteCommandAction.writeCommandAction(getProject(), DomUtil.getFile(domElement)).run(() -> valueSetter.run());
      fireChanged();
    }
  }

}
