// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.ui;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.content.Content;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.ui.TerminalWidgetListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalBundle;
import org.jetbrains.plugins.terminal.TerminalOptionsProvider;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TerminalContainer {

  private static final Logger LOG = Logger.getInstance(TerminalContainer.class);

  private final Content myContent;
  private final JBTerminalWidget myTerminalWidget;
  private final Project myProject;
  private final TerminalView myTerminalView;
  private JPanel myPanel;
  private boolean myForceHideUiWhenSessionEnds = false;
  private final TerminalWidgetListener myListener;

  public TerminalContainer(@NotNull Project project,
                           @NotNull Content content,
                           @NotNull JBTerminalWidget terminalWidget,
                           @NotNull TerminalView terminalView) {
    myProject = project;
    myContent = content;
    myTerminalWidget = terminalWidget;
    myTerminalView = terminalView;
    myPanel = createPanel(terminalWidget);
    myListener = widget -> {
      ApplicationManager.getApplication().invokeLater(() -> processSessionCompleted(), myProject.getDisposed());
    };
    terminalWidget.addListener(myListener);
    terminalView.register(this);
    Disposer.register(content, () -> cleanup());
  }

  public @NotNull JBTerminalWidget getTerminalWidget() {
    return myTerminalWidget;
  }

  public @NotNull Content getContent() {
    return myContent;
  }

  public void closeAndHide() {
    myForceHideUiWhenSessionEnds = true;
    if (myTerminalWidget.getTtyConnector().isConnected()) {
      myTerminalWidget.close();
    }
    else {
      // When "Close session when it ends" is off, terminal session is shown even with terminated shell process.
      processSessionCompleted();
    }
  }

  private static @NotNull JPanel createPanel(@NotNull JBTerminalWidget terminalWidget ) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(null);
    panel.setFocusable(false);
    panel.add(terminalWidget.getComponent(), BorderLayout.CENTER);
    return panel;
  }

  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  public void split(boolean vertically, @NotNull JBTerminalWidget newTerminalWidget) {
    boolean hasFocus = myTerminalWidget.getTerminalPanel().hasFocus();
    JPanel parent = myPanel;
    parent.remove(myTerminalWidget.getComponent());

    myPanel = createPanel(myTerminalWidget);

    Splitter splitter = createSplitter(vertically);
    splitter.setFirstComponent(myPanel);
    TerminalContainer newContainer = new TerminalContainer(myProject, myContent, newTerminalWidget, myTerminalView);
    splitter.setSecondComponent(newContainer.getComponent());

    parent.add(splitter, BorderLayout.CENTER);
    parent.revalidate();
    if (hasFocus) {
      requestFocus(myTerminalWidget);
    }
  }

  private static @NotNull Splitter createSplitter(boolean vertically) {
    Splitter splitter = new OnePixelSplitter(vertically, 0.5f, 0.1f, 0.9f);
    splitter.setDividerWidth(JBUI.scale(1));
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    if (color != null) {
      splitter.getDivider().setBackground(color);
    }
    return splitter;
  }

  private void processSessionCompleted() {
    Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      JBTerminalWidget nextToFocus = null;
      if (myTerminalWidget.getTerminalPanel().hasFocus()) {
        nextToFocus = getNextSplitTerminal(true);
      }
      Splitter splitter = (Splitter)parent;
      parent = parent.getParent();
      JComponent otherComponent = myPanel.equals(splitter.getFirstComponent()) ? splitter.getSecondComponent()
                                                                               : splitter.getFirstComponent();
      Component realComponent = unwrapComponent(otherComponent);
      if (realComponent instanceof JBTerminalWidget) {
        TerminalContainer otherContainer = myTerminalView.getContainer((JBTerminalWidget)realComponent);
        otherContainer.myPanel = (JPanel)parent;
      }
      realComponent.getParent().remove(realComponent);
      parent.remove(splitter);
      parent.add(realComponent, BorderLayout.CENTER);
      parent.revalidate();
      if (nextToFocus != null) {
        requestFocus(nextToFocus);
      }
      cleanup();
      Disposer.dispose(myTerminalWidget);
    }
    else {
      processSingleTerminalCompleted();
    }
  }

  private void cleanup() {
    myTerminalWidget.removeListener(myListener);
    myTerminalView.unregister(this);
  }

  private void processSingleTerminalCompleted() {
    if (myForceHideUiWhenSessionEnds || TerminalOptionsProvider.getInstance().getCloseSessionOnLogout()) {
      myTerminalView.closeTab(myContent);
    }
    else {
      String text = getSessionCompletedMessage(myTerminalWidget);
      myTerminalWidget.writePlainMessage("\n" + text + "\n");
      myTerminalWidget.getTerminalPanel().setCursorVisible(false);
    }
  }

  private static @NotNull @Nls String getSessionCompletedMessage(@NotNull JBTerminalWidget widget) {
    String text = "[" + TerminalBundle.message("session.terminated.text") + "]";
    ProcessTtyConnector connector = ShellTerminalWidget.getProcessTtyConnector(widget.getTtyConnector());
    if (connector != null) {
      Integer exitCode = null;
      try {
        exitCode = connector.getProcess().exitValue();
      }
      catch (IllegalThreadStateException ignored) {
      }
      return text + "\n[" + IdeCoreBundle.message("finished.with.exit.code.text.message", exitCode != null ? exitCode : "unknown") + "]";
    }
    return text;
  }

  public boolean isSplitTerminal() {
    return findRootSplitter() != null;
  }

  public @Nullable JBTerminalWidget getNextSplitTerminal(boolean forward) {
    List<JBTerminalWidget> terminals = listTerminals();
    int ind = terminals.indexOf(myTerminalWidget);
    if (ind < 0) {
      LOG.error("All split terminal list (" + terminals.size() + ") doesn't contain this terminal");
      return null;
    }
    if (terminals.size() == 1) {
      return null;
    }
    int newInd = (ind + (forward ? 1 : (terminals.size() - 1))) % terminals.size();
    return terminals.get(newInd);
  }

  private @NotNull List<JBTerminalWidget> listTerminals() {
    Splitter rootSplitter = findRootSplitter();
    if (rootSplitter == null) {
      return Collections.singletonList(myTerminalWidget);
    }
    List<JBTerminalWidget> terminals = new ArrayList<>();
    traverseSplitters(rootSplitter, terminals);
    return terminals;
  }

  private void traverseSplitters(@NotNull Splitter splitter, @NotNull List<JBTerminalWidget> terminals) {
    traverseParentPanel(splitter.getFirstComponent(), terminals);
    traverseParentPanel(splitter.getSecondComponent(), terminals);
  }

  private void traverseParentPanel(@NotNull JComponent parentPanel, @NotNull List<JBTerminalWidget> terminals) {
    Component[] components = parentPanel.getComponents();
    if (components.length == 1) {
      Component c = components[0];
      if (c instanceof Splitter) {
        traverseSplitters((Splitter)c, terminals);
      }
      else if (c instanceof JBTerminalWidget) {
        terminals.add((JBTerminalWidget)c);
      }
    }
  }

  private @Nullable Splitter findRootSplitter() {
    Splitter splitter = ObjectUtils.tryCast(myPanel.getParent(), Splitter.class);
    while (splitter != null) {
      Component panel = splitter.getParent();
      Splitter parentSplitter = panel != null ? ObjectUtils.tryCast(panel.getParent(), Splitter.class) : null;
      if (parentSplitter != null) {
        splitter = parentSplitter;
      }
      else {
        return splitter;
      }
    }
    return null;
  }

  private static @NotNull Component unwrapComponent(@NotNull JComponent component) {
    Component[] components = component.getComponents();
    if (components.length == 1) {
      Component c = components[0];
      if (c instanceof JBTerminalWidget || c instanceof Splitter) {
        return c;
      }
    }
    LOG.error("Cannot unwrap " + component + ", children: " + Arrays.toString(components));
    return component;
  }

  public void requestFocus(@NotNull JBTerminalWidget terminal) {
    IdeFocusManager.getInstance(myProject).requestFocus(terminal.getTerminalPanel(), true);
  }
}
