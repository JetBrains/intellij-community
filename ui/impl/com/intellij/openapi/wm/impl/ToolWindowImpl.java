package com.intellij.openapi.wm.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ToolWindowImpl implements ToolWindowEx {
  private final PropertyChangeSupport myChangeSupport;
  private final ToolWindowManagerImpl myToolWindowManager;
  private final String myId;
  private JComponent myComponent;
  private boolean myAvailable;
  private ContentManager myContentManager;

  private static final Content EMPTY_CONTENT = new ContentImpl(new JLabel(), "", false);
  private ToolWindowContentUi myContentUI;

  private InternalDecorator myDecorator;

  private boolean myHideOnEmptyContent = false;

  ToolWindowImpl(final ToolWindowManagerImpl toolWindowManager, final String id, boolean canCloseContent, @Nullable final JComponent component) {
    myToolWindowManager = toolWindowManager;
    myChangeSupport = new PropertyChangeSupport(this);
    myId = id;
    myAvailable = true;

    final ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
    myContentUI = new ToolWindowContentUi(this);
    myContentManager =
      contentFactory.createContentManager(myContentUI, canCloseContent, toolWindowManager.getProject());

    if (component != null) {
      final Content content = contentFactory.createContent(component, "", false);
      myContentManager.addContent(content);
      myContentManager.setSelectedContent(content, false);
    }

    myComponent = myContentManager.getComponent();
    myComponent.setFocusCycleRoot(true);
  }

  public final void addPropertyChangeListener(final PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(final PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  public final void activate(final Runnable runnable) {
    activate(runnable, true);
  }

  public void activate(@Nullable final Runnable runnable, final boolean autoFocusContents) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.activateToolWindow(myId, true, autoFocusContents);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final boolean isActive() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowActive(myId);
  }

  public final void show(final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.showToolWindow(myId);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final void hide(final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.hideToolWindow(myId, false);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final boolean isVisible() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowVisible(myId);
  }

  public final ToolWindowAnchor getAnchor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowAnchor(myId);
  }

  public final void setAnchor(final ToolWindowAnchor anchor, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowAnchor(myId, anchor);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final void setAutoHide(final boolean state) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowAutoHide(myId, state);
  }

  public final boolean isAutoHide() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowAutoHide(myId);
  }

  public final boolean isFloating() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowFloating(myId);
  }

  public final ToolWindowType getType() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowType(myId);
  }

  public final void setType(final ToolWindowType type, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowType(myId, type);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final ToolWindowType getInternalType() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowInternalType(myId);
  }

  public final void setAvailable(final boolean available, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Boolean oldAvailable = myAvailable ? Boolean.TRUE : Boolean.FALSE;
    myAvailable = available;
    myChangeSupport.firePropertyChange(PROP_AVAILABLE, oldAvailable, myAvailable ? Boolean.TRUE : Boolean.FALSE);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public void installWatcher(ContentManager contentManager) {
    new ContentManagerWatcher(this, contentManager);
  }

  /**
   * @return <code>true</code> if the component passed into constructor is not instance of
   *         <code>ContentManager</code> class. Otherwise it delegates the functionality to the
   *         passed content manager.
   */
  public final boolean isAvailable() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myAvailable && myComponent != null;
  }

  public final JComponent getComponent() {
    return myComponent;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  public ToolWindowContentUi getContentUI() {
    return myContentUI;
  }

  public final Icon getIcon() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getSelectedContent().getIcon();
  }

  public final String getId() {
    return myId;
  }

  public final String getTitle() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getSelectedContent().getDisplayName();
  }

  public final void setIcon(final Icon icon) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Icon oldIcon = getIcon();
    getSelectedContent().setIcon(icon);
    myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, icon);
  }

  public final void setTitle(final String title) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final String oldTitle = getTitle();
    getSelectedContent().setDisplayName(title);
    myChangeSupport.firePropertyChange(PROP_TITLE, oldTitle, title);
  }

  private Content getSelectedContent() {
    final Content selected = getContentManager().getSelectedContent();
    return selected != null ? selected : EMPTY_CONTENT;
  }

  public void setDecorator(final InternalDecorator decorator) {
    myDecorator = decorator;
  }

  public void fireActivated() {
    if (myDecorator != null) {
      myDecorator.fireActivated();
    }
  }

  public void fireHidden() {
    if (myDecorator != null) {
      myDecorator.fireHidden();
    }
  }

  public void fireHiddenSide() {
    if (myDecorator != null) {
      myDecorator.fireHiddenSide();
    }
  }


  public ToolWindowManagerImpl getToolWindowManager() {
    return myToolWindowManager;
  }

  @Nullable
  public ActionGroup getPopupGroup() {
    return myDecorator != null ? myDecorator.createPopupGroup() : null;
  }

  public void setDefaultState(@Nullable final ToolWindowAnchor anchor, @Nullable final ToolWindowType type, @Nullable final Rectangle floatingBounds) {
    myToolWindowManager.setDefaultState(this, anchor, type, floatingBounds);
  }

  public void setToHideOnEmptyContent(final boolean hideOnEmpty) {
    myHideOnEmptyContent = hideOnEmpty;
  }

  public boolean isToHideOnEmptyContent() {
    return myHideOnEmptyContent;
  }

  public boolean isDisposed() {
    return myContentManager.isDisposed();
  }
}