package com.intellij.util.config;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

import javax.swing.*;

public class ToggleBooleanProperty extends ToggleAction {
  private final AbstractProperty.AbstractPropertyContainer myProperties;
  private final AbstractProperty<Boolean> myProperty;

  public ToggleBooleanProperty(String text, String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
    super(text, description, icon);
    myProperties = properties;
    myProperty = property;
  }

  public boolean isSelected(AnActionEvent e) {
    return myProperty.get(myProperties).booleanValue();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    myProperty.set(myProperties, Boolean.valueOf(state));
  }

  protected AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  public static abstract class Disablable extends ToggleBooleanProperty {
    public Disablable(String text, String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
      super(text, description, icon, properties, property);
    }

    protected abstract boolean isEnabled();

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(isEnabled());
    }
  }
}
