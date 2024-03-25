// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    for (DataProvider provider : myCustomDataProviders) {
      Object data = provider.getData(dataId);
      if (data != null) return data;
    }
    return super.getData(dataId);
  }

  @Override
  protected void wrapValueSetting(final @NotNull DomElement domElement, final Runnable valueSetter) {
    if (domElement.isValid()) {
      WriteCommandAction.writeCommandAction(getProject(), DomUtil.getFile(domElement)).run(() -> valueSetter.run());
      fireChanged();
    }
  }

}
