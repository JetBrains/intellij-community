package com.intellij.openapi.wm.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.ContentManager;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ToolWindowImpl implements ToolWindowEx{
  private final PropertyChangeSupport myChangeSupport;
  private final ToolWindowManagerImpl myToolWindowManager;
  private final String myId;
  private Icon myIcon;
  private final JComponent myComponent;
  private boolean myAvailable;
  private String myTitle;

  ToolWindowImpl(
    final ToolWindowManagerImpl toolWindowManager,
    final String id,
    final JComponent component
  ){
    myToolWindowManager=toolWindowManager;
    myChangeSupport=new PropertyChangeSupport(this);
    myId=id;
    myComponent=component;
    myAvailable = true;
  }

  public final void addPropertyChangeListener(final PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(final PropertyChangeListener l){
    myChangeSupport.removePropertyChangeListener(l);
  }

  public final void activate(final Runnable runnable){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.activateToolWindow(myId);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final boolean isActive(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowActive(myId);
  }

  public final void show(final Runnable runnable){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.showToolWindow(myId);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final void hide(final Runnable runnable){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.hideToolWindow(myId, false);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final boolean isVisible(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowVisible(myId);
  }

  public final ToolWindowAnchor getAnchor(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowAnchor(myId);
  }

  public final void setAnchor(final ToolWindowAnchor anchor, final Runnable runnable){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowAnchor(myId,anchor);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final void setAutoHide(final boolean state){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowAutoHide(myId,state);
  }

  public final boolean isAutoHide(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowAutoHide(myId);
  }

  public final boolean isFloating(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowFloating(myId);
  }

  public final ToolWindowType getType(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowType(myId);
  }

  public final void setType(final ToolWindowType type, final Runnable runnable){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowType(myId,type);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final ToolWindowType getInternalType(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowInternalType(myId);
  }

  public final void setAvailable(final boolean available,final Runnable runnable){
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Boolean oldAvailable=myAvailable?Boolean.TRUE:Boolean.FALSE;
    myAvailable=available;
    myChangeSupport.firePropertyChange(PROP_AVAILABLE,oldAvailable,myAvailable?Boolean.TRUE:Boolean.FALSE);
    if(runnable!=null){
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public void installWatcher(ContentManager contentManager) {
    new ContentManagerWatcher(this, contentManager);
  }

  /**
   * @return <code>true</code> if the component passed into constructor is not instance of
   * <code>ContentManager</code> class. Otherwise it delegates the functionality to the
   * passed content manager.
   */
  public final boolean isAvailable(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myAvailable && myComponent != null;
  }

  public final JComponent getComponent(){
    return myComponent;
  }

  public final Icon getIcon(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myIcon;
  }

  final String getId(){
    return myId;
  }

  public final String getTitle(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myTitle;
  }

  public final void setIcon(final Icon icon){
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Icon oldIcon=myIcon;
    myIcon=icon;
    myChangeSupport.firePropertyChange(PROP_ICON,oldIcon,myIcon);
  }

  public final void setTitle(final String title){
    ApplicationManager.getApplication().assertIsDispatchThread();
    final String oldTitle=myTitle;
    myTitle=title;
    myChangeSupport.firePropertyChange(PROP_TITLE,oldTitle,myTitle);
  }

}