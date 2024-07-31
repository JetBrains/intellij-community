// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    for (DataProvider provider : myCustomDataProviders) {
      DataSink.uiDataSnapshot(sink, provider);
    }
  }

  @Override
  protected void wrapValueSetting(final @NotNull DomElement domElement, final Runnable valueSetter) {
    if (domElement.isValid()) {
      WriteCommandAction.writeCommandAction(getProject(), DomUtil.getFile(domElement)).run(() -> valueSetter.run());
      fireChanged();
    }
  }

}
