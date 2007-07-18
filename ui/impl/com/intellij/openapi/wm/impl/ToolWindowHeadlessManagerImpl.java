/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 13-Jul-2006
 * Time: 12:07:39
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.ContentFactory;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.awt.*;

@SuppressWarnings({"ConstantConditions"})
public class ToolWindowHeadlessManagerImpl extends ToolWindowManagerEx {
  private static final ToolWindow HEADLESS_WINDOW = new ToolWindow(){
    public boolean isActive() {
      return false;
    }

    public void activate(@Nullable Runnable runnable) {
    }

    public boolean isVisible() {
      return false;
    }

    public void show(@Nullable Runnable runnable) {
    }

    public void hide(@Nullable Runnable runnable) {
    }

    public ToolWindowAnchor getAnchor() {
      return ToolWindowAnchor.BOTTOM;
    }

    public void setAnchor(ToolWindowAnchor anchor, @Nullable Runnable runnable) {
    }

    public boolean isAutoHide() {
      return false;
    }

    public void setAutoHide(boolean state) {
    }

    public void setToHideOnEmptyContent(final boolean hideOnEmpty) {
    }

    public boolean isToHideOnEmptyContent() {
      return false;
    }

    public ToolWindowType getType() {
      return ToolWindowType.SLIDING;
    }

    public void setType(ToolWindowType type, @Nullable Runnable runnable) {
    }

    public Icon getIcon() {
      return null;
    }

    public void setIcon(Icon icon) {
    }

    public String getTitle() {
      return "";
    }

    public void setTitle(String title) {
    }

    public boolean isAvailable() {
      return false;
    }

    public void setAvailable(boolean available, @Nullable Runnable runnable) {
    }

    public void installWatcher(ContentManager contentManager) {
    }

    public JComponent getComponent() {
      return null;
    }

    public ContentManager getContentManager() {
      return MOCK_CONTENT_MANAGER;
    }

    public void setDefaultState(@Nullable final ToolWindowAnchor anchor, @Nullable final ToolWindowType type, @Nullable final Rectangle floatingBounds) {
    }
  };

  @NonNls private static final ContentManager MOCK_CONTENT_MANAGER = new ContentManager() {
    public void addContent(final Content content) { }
    public void addContent(final Content content, final Object constraints) { }
    public void addContentManagerListener(final ContentManagerListener l) { }
    public void addDataProvider(final DataProvider provider) { }
    public void addSelectedContent(final Content content) { }
    public boolean canCloseAllContents() { return false; }
    public boolean canCloseContents() { return false; }
    public Content findContent(final String displayName) { return null; }
    public List<AnAction> getAdditionalPopupActions(final Content content) { return Collections.emptyList(); }
    public String getCloseActionName() { return "close"; }
    public String getCloseAllButThisActionName() { return "closeallbutthis"; }
    public JComponent getComponent() { return new JLabel(); }
    public Content getContent(final JComponent component) { return null; }
    @Nullable
    public Content getContent(final int index) { return null; }
    public int getContentCount() { return 0; }
    public Content[] getContents() { return new Content[0]; }
    public int getIndexOfContent(final Content content) { return -1; }
    @Nullable
    public Content getSelectedContent() { return null; }
    public Content[] getSelectedContents() { return new Content[0]; }
    public boolean isSelected(final Content content) { return false; }
    public void removeAllContents() { }
    public boolean removeContent(final Content content) { return false; }
    public void removeContentManagerListener(final ContentManagerListener l) { }
    public void removeSelectedContent(final Content content) { }
    public void requestFocus(@Nullable final Content content) { }
    public void selectNextContent() { }
    public void selectPreviousContent() { }
    public void setSelectedContent(final Content content) { }
    public void setSelectedContent(final Content content, final boolean requestFocus) { }


    public ContentFactory getFactory() {
      return PeerFactory.getInstance().getContentFactory();
    }
  };

  public ToolWindow registerToolWindow(String id, JComponent component, ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(String id, JComponent component, ToolWindowAnchor anchor, Disposable parentDisposable) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor,
                                       final Disposable parentDisposable) {
    return HEADLESS_WINDOW;
  }

  public void unregisterToolWindow(String id) {
  }

  public void activateEditorComponent() {
  }

  public boolean isEditorComponentActive() {
    return false;
  }

  public String[] getToolWindowIds() {
    return new String[0];
  }

  public String getActiveToolWindowId() {
    return null;
  }

  public ToolWindow getToolWindow(String id) {
    return HEADLESS_WINDOW;
  }

  public void invokeLater(Runnable runnable) {
  }

  public void addToolWindowManagerListener(ToolWindowManagerListener l) {

  }

  public void removeToolWindowManagerListener(ToolWindowManagerListener l) {
  }

  public String getLastActiveToolWindowId() {
    return null;
  }

  public String getLastActiveToolWindowId(Condition<JComponent> condition) {
    return null;
  }

  public DesktopLayout getLayout() {
    return new DesktopLayout();
  }

  public void setLayoutToRestoreLater(DesktopLayout layout) {
  }

  public DesktopLayout getLayoutToRestoreLater() {
    return new DesktopLayout();
  }

  public void setLayout(DesktopLayout layout) {
  }

  public void clearSideStack() {
  }

  public void hideToolWindow(final String id, final boolean hideSide) {
  }
}