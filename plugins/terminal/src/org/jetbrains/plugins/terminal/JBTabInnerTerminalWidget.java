// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;

import java.util.function.Function;

/**
 * @author traff
 */
public class JBTabInnerTerminalWidget extends JBTerminalWidget {
  private final Function<String, JBTabInnerTerminalWidget> myCreateNewSessionAction;
  private JBTabbedTerminalWidget myTabbedWidget;

  public JBTabInnerTerminalWidget(Project project,
                                  JBTerminalSystemSettingsProviderBase settingsProvider,
                                  Disposable parent,
                                  Function<String, JBTabInnerTerminalWidget> createNewSessionAction,
                                  JBTabbedTerminalWidget tabbedWidget) {
    this(project, 80, 24, settingsProvider, parent, pair -> createNewSessionAction.apply(pair));
    myTabbedWidget = tabbedWidget;
  }

  public JBTabInnerTerminalWidget(Project project,
                                  int columns,
                                  int lines,
                                  JBTerminalSystemSettingsProviderBase settingsProvider,
                                  Disposable parent,
                                  Function<String, JBTabInnerTerminalWidget> createNewSessionAction) {
    super(project, columns, lines, settingsProvider, parent);
    myCreateNewSessionAction = createNewSessionAction;
  }


  public Function<String, JBTabInnerTerminalWidget> getCreateNewSessionAction() {
    return myCreateNewSessionAction;
  }

  public JBTabbedTerminalWidget getTabbedWidget() {
    return myTabbedWidget;
  }
}
