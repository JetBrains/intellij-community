/**
 * @author cdr
 */
package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;

import javax.swing.*;
import java.awt.*;

public abstract class TextAttributesDescription extends ColorAndFontDescription {
  private TextAttributes myAttributes;

  public TextAttributesDescription(String name,
                                   String group,
                                   TextAttributes attributes,
                                   TextAttributesKey type,
                                   EditorColorsScheme scheme, Icon icon, final String toolTip) {
    super(name, group, type == null ? null : type.getExternalName(), scheme, icon, toolTip);
    myAttributes = attributes;
    initCheckedStatus();
  }

  public int getFontType() {
    return myAttributes.getFontType();
  }

  public void setFontType(int type) {
    myAttributes.setFontType(type);
  }

  public Color getExternalEffectColor() {
    return myAttributes.getEffectColor();
  }

  public EffectType getExternalEffectType() {
    return myAttributes.getEffectType();
  }

  public void setExternalEffectColor(Color color) {
    myAttributes.setEffectColor(color);
  }

  public void setExternalEffectType(EffectType type) {
    myAttributes.setEffectType(type);
  }

  public Color getExternalForeground() {
    return myAttributes.getForegroundColor();
  }

  public void setExternalForeground(Color col) {
    myAttributes.setForegroundColor(col);
  }

  public Color getExternalBackground() {
    return myAttributes.getBackgroundColor();
  }

  public Color getExternalErrorStripe() {
    return myAttributes.getErrorStripeColor();
  }

  public void setExternalBackground(Color col) {
    myAttributes.setBackgroundColor(col);
  }

  public void setExternalErrorStripe(Color col) {
    myAttributes.setErrorStripeColor(col);
  }

  protected TextAttributes getTextAttributes() {
    return myAttributes;
  }
}