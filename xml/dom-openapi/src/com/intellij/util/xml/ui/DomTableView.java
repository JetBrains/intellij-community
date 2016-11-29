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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class DomTableView extends AbstractTableView<DomElement> {
  private final List<TypeSafeDataProvider> myCustomDataProviders = new SmartList<>();

  public DomTableView(final Project project) {
    super(project);
  }

  public DomTableView(final Project project, final String emptyPaneText, final String helpID) {
    super(project, emptyPaneText, helpID);
  }

  public void addCustomDataProvider(TypeSafeDataProvider provider) {
    myCustomDataProviders.add(provider);
  }

  @Override
  public void calcData(final DataKey key, final DataSink sink) {
    super.calcData(key, sink);
    for (final TypeSafeDataProvider customDataProvider : myCustomDataProviders) {
      customDataProvider.calcData(key, sink);
    }
  }

  @Deprecated
  protected final void installPopup(final DefaultActionGroup group) {
    installPopup(ActionPlaces.J2EE_ATTRIBUTES_VIEW_POPUP, group);
  }

  @Override
  protected void wrapValueSetting(@NotNull final DomElement domElement, final Runnable valueSetter) {
    if (domElement.isValid()) {
      new WriteCommandAction(getProject(), DomUtil.getFile(domElement)) {
        @Override
        protected void run(@NotNull final Result result) throws Throwable {
          valueSetter.run();
        }
      }.execute();
      fireChanged();
    }
  }

}
