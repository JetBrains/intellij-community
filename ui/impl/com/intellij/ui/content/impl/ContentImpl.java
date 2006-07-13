
package com.intellij.ui.content.impl;

import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Hashtable;

public class ContentImpl implements Content {
  private String myDisplayName;
  private String myDescription;
  private JComponent myComponent;
  private Icon myIcon;
  private PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  private ContentManager myManager = null;
  private Hashtable myUserData = new Hashtable();
  private boolean myIsLocked = false;
  private boolean myPinnable = true;
  private Icon myLayeredIcon = new LayeredIcon(2);
  private Disposable myDisposer = null;
  private String myTabName;
  private String myToolwindowTitle;

  public ContentImpl(JComponent component, String displayName, boolean isPinnable) {
    myComponent = component;
    myDisplayName = displayName;
    myPinnable = isPinnable;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setComponent(JComponent component) {
    Component oldComponent = myComponent;
    myComponent = component;
    myChangeSupport.firePropertyChange(PROP_COMPONENT, oldComponent, myComponent);
  }

  public void setIcon(Icon icon) {
    Icon oldValue = getIcon();
    myIcon = icon;
    myLayeredIcon = IconUtilEx.createLayeredIcon(myIcon, IconLoader.getIcon("/nodes/tabPin.png"));
    myChangeSupport.firePropertyChange(PROP_ICON, oldValue, getIcon());
  }

  public Icon getIcon() {
    if (myIsLocked) {
      return myIcon == null ? IconLoader.getIcon("/nodes/pin.png") : myLayeredIcon;
    }
    else {
      return myIcon;
    }
  }

  public void setDisplayName(String displayName) {
    String oldValue = myDisplayName;
    myDisplayName = displayName;
    myChangeSupport.firePropertyChange(PROP_DISPLAY_NAME, oldValue, myDisplayName);
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public void setTabName(String tabName) {
    myTabName = tabName;
  }

  public String getTabName() {
    if (myTabName != null) return myTabName;
    return myDisplayName;
  }

  public void setToolwindowTitle(String toolwindowTitle) {
    myToolwindowTitle = toolwindowTitle;
  }

  public String getToolwindowTitle() {
    if (myToolwindowTitle != null) return myToolwindowTitle;
    return myDisplayName;
  }

  public Disposable getDisposer() {
    return myDisposer;
  }

  public void setDisposer(Disposable disposer) {
    myDisposer = disposer;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    String oldValue = myDescription;
    myDescription = description;
    myChangeSupport.firePropertyChange(PROP_DESCRIPTION, oldValue, myDescription);
  }

  public void addPropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  public void setManager(ContentManager manager) {
    myManager = manager;
  }

  public ContentManager getManager() {
    return myManager;
  }

  public boolean isSelected() {
    if (myManager == null) return false;
    return (myManager.getSelectedContent() == this);
  }

  public <T> void putUserData(Key<T> key, T value) {
    if (key == null) return;
    if (value != null){
      myUserData.put(key, value);
    }
    else{
      myUserData.remove(key);
    }
  }

  public <T> T getUserData(Key<T> key) {
    if (key == null) return null;
    return (T)myUserData.get(key);
  }

  public final void release() {
    myComponent = null;
    myManager = null;
    if (myUserData != null) {
      myUserData.clear();
    }
    myUserData = null;

    if (myDisposer != null) {
      myDisposer.dispose();
      myDisposer = null;
    }
  }

  //TODO[anton,vova] investigate
  public boolean isValid() {
    return myManager != null;
  }

  public boolean isPinned() {
    return myIsLocked;
  }

  public void setPinned(boolean locked) {
    if (isPinnable()) {
      Icon oldIcon = getIcon();
      myIsLocked = locked;
      Icon newIcon = getIcon();
      myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, newIcon);
    }
  }

  public boolean isPinnable() {
    return myPinnable;
  }
}
