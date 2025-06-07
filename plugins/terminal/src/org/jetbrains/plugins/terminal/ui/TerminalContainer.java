// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.ui;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.content.Content;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalBundle;
import org.jetbrains.plugins.terminal.TerminalOptionsProvider;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class TerminalContainer {

  private static final Logger LOG = Logger.getInstance(TerminalContainer.class);

  @ApiStatus.Internal
  public static final @NotNull DataKey<TerminalWidget> TERMINAL_WIDGET_DATA_KEY = DataKey.create("terminalWidget");

  private final Content myContent;
  private final TerminalWidget myTerminalWidget;
  private final Project myProject;
  private final TerminalToolWindowManager myTerminalToolWindowManager;
  private @Nullable TerminalWrapperPanel myWrapperPanel;
  private boolean myForceHideUiWhenSessionEnds = false;

  public TerminalContainer(@NotNull Project project,
                           @NotNull Content content,
                           @NotNull TerminalWidget terminalWidget,
                           @NotNull TerminalToolWindowManager terminalToolWindowManager) {
    myProject = project;
    myContent = content;
    myTerminalWidget = terminalWidget;
    myTerminalToolWindowManager = terminalToolWindowManager;
    terminalWidget.addTerminationCallback(() -> {
      ApplicationManager.getApplication().invokeLater(() -> processSessionCompleted(), myProject.getDisposed());
    }, terminalWidget);
    terminalToolWindowManager.register(this);
    Disposer.register(content, () -> cleanup());
  }

  public @NotNull TerminalWidget getTerminalWidget() {
    return myTerminalWidget;
  }

  public @NotNull Content getContent() {
    return myContent;
  }

  public void closeAndHide() {
    myForceHideUiWhenSessionEnds = true;
    TtyConnector connector = myTerminalWidget.getTtyConnector();
    if (connector != null && connector.isConnected()) {
      connector.close();
    }
    else {
      // When "Close session when it ends" is off, terminal session is shown even with terminated process.
      processSessionCompleted();
    }
  }

  public @NotNull TerminalWrapperPanel getWrapperPanel() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new TerminalWrapperPanel(this);
    }
    return myWrapperPanel;
  }

  public void split(boolean vertically, @NotNull TerminalWidget newTerminalWidget) {
    boolean hasFocus = myTerminalWidget.hasFocus();
    TerminalWrapperPanel newParent = getWrapperPanel();
    myWrapperPanel = new TerminalWrapperPanel(this);
    TerminalContainer newContainer = new TerminalContainer(myProject, myContent, newTerminalWidget, myTerminalToolWindowManager);
    Splitter splitter = createSplitter(vertically, myWrapperPanel, newContainer.getWrapperPanel());
    newParent.setChildSplitter(splitter);
    if (hasFocus) {
      myTerminalWidget.requestFocus();
    }
  }

  private static @NotNull Splitter createSplitter(boolean vertically,
                                                  @NotNull JComponent firstComponent, @NotNull JComponent secondComponent) {
    Splitter splitter = new OnePixelSplitter(vertically, 0.5f, 0.1f, 0.9f);
    splitter.setDividerWidth(JBUI.scale(1));
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    if (color != null) {
      splitter.getDivider().setBackground(color);
    }
    splitter.setFirstComponent(firstComponent);
    splitter.setSecondComponent(secondComponent);
    return splitter;
  }

  private void processSessionCompleted() {
    TerminalWrapperPanel thisPanel = getWrapperPanel();
    if (thisPanel.getParent() instanceof Splitter splitter) {
      TerminalWidget nextToFocus = myTerminalWidget.hasFocus() ? getNextSplitTerminal(true) : null;
      TerminalWrapperPanel parent = getSplitterParent(splitter);
      TerminalWrapperPanel otherPanel = splitter.getFirstComponent().equals(thisPanel) ? (TerminalWrapperPanel)splitter.getSecondComponent()
                                                                                       : (TerminalWrapperPanel)splitter.getFirstComponent();
      parent.transferChildFrom(otherPanel);
      if (nextToFocus != null) {
        nextToFocus.requestFocus();
      }
      cleanup();
      Disposer.dispose(myTerminalWidget);
    }
    else {
      processSingleTerminalCompleted();
    }
  }

  private void cleanup() {
    myTerminalToolWindowManager.unregister(this);
  }

  private void processSingleTerminalCompleted() {
    if (myForceHideUiWhenSessionEnds || TerminalOptionsProvider.getInstance().getCloseSessionOnLogout()) {
      myTerminalToolWindowManager.closeTab(myContent);
    }
    else {
      String text = getSessionCompletedMessage(myTerminalWidget);
      myTerminalWidget.writePlainMessage("\n" + text + "\n");
      myTerminalWidget.setCursorVisible(false);
    }
  }

  private static @NotNull @Nls String getSessionCompletedMessage(@NotNull TerminalWidget widget) {
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
    return getParentSplitter(myWrapperPanel) != null;
  }

  public @Nullable TerminalWidget getNextSplitTerminal(boolean forward) {
    List<TerminalWidget> terminals = listTerminals();
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

  private @NotNull List<TerminalWidget> listTerminals() {
    Splitter rootSplitter = findRootSplitter();
    if (rootSplitter == null) {
      return List.of(myTerminalWidget);
    }
    List<TerminalWidget> terminals = new ArrayList<>();
    traverseSplitters(rootSplitter, terminals);
    return terminals;
  }

  private void traverseSplitters(@NotNull Splitter splitter, @NotNull List<TerminalWidget> terminals) {
    traverseWrapperPanel((TerminalWrapperPanel)splitter.getFirstComponent(), terminals);
    traverseWrapperPanel((TerminalWrapperPanel)splitter.getSecondComponent(), terminals);
  }

  private void traverseWrapperPanel(@NotNull TerminalWrapperPanel panel, @NotNull List<TerminalWidget> terminals) {
    Object child = panel.validateAndGetChild();
    if (child instanceof Splitter splitter) {
      traverseSplitters(splitter, terminals);
    }
    else {
      terminals.add(((TerminalContainer)child).myTerminalWidget);
    }
  }

  private @Nullable Splitter findRootSplitter() {
    Splitter splitter = getParentSplitter(myWrapperPanel);
    while (splitter != null) {
      Splitter parentSplitter = getParentSplitter(getSplitterParent(splitter));
      if (parentSplitter == null) break;
      splitter = parentSplitter;
    }
    return splitter;
  }

  private static @Nullable Splitter getParentSplitter(@Nullable TerminalWrapperPanel panel) {
    return panel != null ? ObjectUtils.tryCast(panel.getParent(), Splitter.class) : null;
  }

  private static @NotNull TerminalWrapperPanel getSplitterParent(@NotNull Splitter splitter) {
    return (TerminalWrapperPanel)splitter.getParent();
  }

  private static final class TerminalWrapperPanel extends JPanel implements UiDataProvider {
    private TerminalContainer myTerminal;

    private TerminalWrapperPanel(@NotNull TerminalContainer terminal) {
      super(new BorderLayout());
      setBorder(null);
      setFocusable(false);
      setChildTerminal(terminal);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      if (myTerminal != null) {
        sink.set(TERMINAL_WIDGET_DATA_KEY, myTerminal.getTerminalWidget());
      }
    }

    private void setChildTerminal(@NotNull TerminalContainer terminal) {
      if (myTerminal != null) {
        throw new IllegalStateException("Cannot set a new terminal when another terminal is still set");
      }
      myTerminal = terminal;
      myTerminal.myWrapperPanel = this;
      setChildComponent(terminal.myTerminalWidget.getComponent());
    }

    private void setChildSplitter(@NotNull Splitter splitter) {
      myTerminal = null;
      setChildComponent(splitter);
    }

    private void setChildComponent(@NotNull Component childComponent) {
      Container parent = childComponent.getParent();
      if (parent != null) {
        parent.remove(childComponent);
      }
      removeAll();
      add(childComponent, BorderLayout.CENTER);
      revalidate();
    }

    private void transferChildFrom(@NotNull TerminalWrapperPanel other) {
      Object childObj = other.validateAndGetChild();
      if (childObj instanceof TerminalContainer otherTerminal) {
        setChildTerminal(otherTerminal);
      }
      else {
        setChildSplitter((Splitter)childObj);
      }
    }

    private @NotNull Object /* TerminalContainer | Splitter */ validateAndGetChild() {
      Component[] children = getComponents();
      if (children.length != 1) {
        throw new IllegalStateException("Expected 1 child, but got " + children.length + ": " + Arrays.toString(children));
      }
      Component child = Objects.requireNonNull(children[0]);
      if (myTerminal != null) {
        if (child == myTerminal.myTerminalWidget.getComponent()) {
          return myTerminal;
        }
        throw new IllegalStateException("Expected terminal widget (" + myTerminal.myTerminalWidget.getComponent() + "), got " + child);
      }
      else {
        if (child instanceof Splitter splitter) {
          return splitter;
        }
        throw new IllegalStateException("Expected splitter, got " + child);
      }
    }
  }
}
