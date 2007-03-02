package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionGroup;

import javax.swing.*;
import java.beans.PropertyChangeSupport;

public final class TabInfo {

  static final String ACTION_GROUP = "actionGroup";
  static final String ICON = "icon";
  static final String TEXT = "text";

  private JComponent myComponent;

  private ActionGroup myGroup;

  private PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);

  private String myText;
  private Icon myIcon;
  private String myPlace;

  TabInfo(final JComponent component) {
    myComponent = component;
  }

  PropertyChangeSupport getChangeSupport() {
    return myChangeSupport;
  }

  public TabInfo setText(String text) {
    String old = myText;
    myText = text;
    myChangeSupport.firePropertyChange(TEXT, old, text);
    return this;
  }

  public TabInfo setIcon(Icon icon) {
    Icon old = myIcon;
    myIcon = icon;
    myChangeSupport.firePropertyChange(ICON, old, icon);
    return this;
  }

  ActionGroup getGroup() {
    return myGroup;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public String getText() {
    return myText;
  }

  Icon getIcon() {
    return myIcon;
  }

  String getPlace() {
    return myPlace;
  }

  public TabInfo setActions(ActionGroup group, String place) {
    ActionGroup old = myGroup;
    myGroup = group;
    myPlace = place;
    myChangeSupport.firePropertyChange(ACTION_GROUP, old, myGroup);
    return this;
  }



}
