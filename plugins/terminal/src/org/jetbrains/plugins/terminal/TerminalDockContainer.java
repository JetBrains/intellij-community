// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.*;

import static org.jetbrains.plugins.terminal.TerminalToolWindowManager.findNearestContentManager;

/**
 * Terminal tab can be moved to the editor using {@link org.jetbrains.plugins.terminal.action.MoveTerminalSessionToEditorAction}.
 * This dock container is responsible for an ability to drop the editor tab with the terminal
 * back to the terminal tool window.
 */
final class TerminalDockContainer implements DockContainer {
  private final @NotNull ToolWindowEx myToolWindow;

  TerminalDockContainer(@NotNull ToolWindowEx toolWindow) {
    myToolWindow = toolWindow;
  }

  @Override
  public @NotNull RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(myToolWindow.getDecorator());
  }

  @Override
  public @NotNull ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
    return isTerminalSessionContent(content) ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
  }

  @Override
  public @NotNull JComponent getContainerComponent() {
    return myToolWindow.getDecorator();
  }

  @Override
  public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
    if (dropTarget == null) return;
    if (isTerminalSessionContent(content)) {
      // Find the right split to create the new tab in
      Component component = dropTarget.getOriginalComponent();
      Point point = dropTarget.getOriginalPoint();
      Component deepestComponent = UIUtil.getDeepestComponentAt(component, point.x, point.y);
      ContentManager nearestManager = findNearestContentManager(deepestComponent);

      TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)content.getKey();
      var engine = TerminalEngine.CLASSIC; // Engine doesn't matter here because we will reuse the existing terminal widget.
      var manager = TerminalToolWindowManager.getInstance(myToolWindow.getProject());
      Content newContent = manager.createNewTab(nearestManager, terminalFile.getTerminalWidget(), manager.getTerminalRunner(), engine,
                                                null, null, null, true, true);
      newContent.setDisplayName(terminalFile.getName());
    }
  }

  private static boolean isTerminalSessionContent(@NotNull DockableContent<?> content) {
    return content.getKey() instanceof TerminalSessionVirtualFileImpl;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return false;
  }
}