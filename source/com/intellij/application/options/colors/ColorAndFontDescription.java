package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

public abstract class ColorAndFontDescription extends TextAttributes implements EditorSchemeAttributeDescriptor {
  private String myName;
  private String myGroup;
  private String myType;
  private EditorColorsScheme myScheme;
  private boolean isForegroundChecked;
  private boolean isBackgroundChecked;
  private boolean myIsEffectsColorChecked;
  private boolean isErrorStripeChecked;

  public ColorAndFontDescription(String name, String group, String type, EditorColorsScheme scheme) {
    myName = name;
    myGroup = group;
    myType = type;
    myScheme = scheme;
  }

  public String toString() {
    return myName;
  }

  public String getGroup() {
    return myGroup;
  }

  public String getType() {
    return myType;
  }

  public EditorColorsScheme getScheme() {
    return myScheme;
  }

  protected void initCheckedStatus() {
    isForegroundChecked = getExternalForeground() != null;
    isBackgroundChecked = getExternalBackground() != null;
    isErrorStripeChecked = getExternalErrorStripe() != null;
    myIsEffectsColorChecked = getExternalEffectColor() != null;
    super.setForegroundColor(getExternalForeground());
    super.setBackgroundColor(getExternalBackground());
    super.setEffectColor(getExternalEffectColor());
    super.setEffectType(getExternalEffectType());
    super.setErrorStripeColor(getExternalErrorStripe());
  }

  public abstract Color getExternalForeground();

  public abstract Color getExternalBackground();

  public abstract Color getExternalErrorStripe();

  public abstract Color getExternalEffectColor();

  public abstract EffectType getExternalEffectType();

  public abstract void setExternalForeground(Color col);

  public abstract void setExternalBackground(Color col);

  public abstract void setExternalErrorStripe(Color col);

  public abstract void setExternalEffectColor(Color color);

  public abstract void setExternalEffectType(EffectType type);

  public final void setForegroundColor(Color col) {
    super.setForegroundColor(col);
    if (isForegroundChecked) {
      setExternalForeground(col);
    } else {
      setExternalForeground(null);
    }
  }

  public final void setBackgroundColor(Color col) {
    super.setBackgroundColor(col);
    if (isBackgroundChecked) {
      setExternalBackground(col);
    } else {
      setExternalBackground(null);
    }
  }

  public void setErrorStripeColor(Color color) {
    super.setErrorStripeColor(color);
    if (isErrorStripeChecked) {
      setExternalErrorStripe(color);
    }
    else {
      setExternalErrorStripe(null);
    }
  }

  public final void setEffectColor(Color col) {
    super.setEffectColor(col);
    if (myIsEffectsColorChecked) {
      setExternalEffectColor(col);
    } else {
      setExternalEffectColor(null);
    }
  }

  public final void setEffectType(EffectType effectType) {
    super.setEffectType(effectType);
    setExternalEffectType(effectType);
  }

  public boolean isForegroundChecked() {
    return isForegroundChecked;
  }

  public boolean isBackgroundChecked() {
    return isBackgroundChecked;
  }

  public boolean isErrorStripeChecked() {
    return isErrorStripeChecked;
  }

  public boolean isEffectsColorChecked() {
    return myIsEffectsColorChecked;
  }

  public final void setForegroundChecked(boolean val) {
    isForegroundChecked = val;
    setForegroundColor(getForegroundColor());
  }

  public final void setBackgroundChecked(boolean val) {
    isBackgroundChecked = val;
    setBackgroundColor(getBackgroundColor());
  }

  public final void setErrorStripeChecked(boolean val) {
    isErrorStripeChecked = val;
    setErrorStripeColor(getErrorStripeColor());
  }

  public final void setEffectsColorChecked(boolean val) {
    myIsEffectsColorChecked = val;
    setEffectColor(getEffectColor());
    setEffectType(getEffectType());
  }

  public abstract int getFontType();

  public abstract void setFontType(int type);

  public boolean isFontEnabled() {
    return true;
  }

  public boolean isForegroundEnabled() {
    return true;
  }

  public boolean isBackgroundEnabled() {
    return true;
  }

  public boolean isErrorStripeEnabled() {
    return false;
  }

  public boolean isEffectsColorEnabled() {
    return true;
  }

  public boolean isModified() {
    return false;
  }
}