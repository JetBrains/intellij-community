/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.HashMap;

import java.awt.*;
import java.util.Map;

public class EditorColorsSchemeImpl extends AbstractColorsScheme {

  public EditorColorsSchemeImpl(EditorColorsScheme parenScheme, DefaultColorSchemesManager defaultColorSchemesManager) {
    super(parenScheme, defaultColorSchemesManager);
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------
  public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
    myAttributesMap.put(key, attributes);
  }

  public void setColor(ColorKey key, Color color) {
    myColorsMap.put(key, color);
  }

  public void setFont(EditorFontType key, Font font) {
    myValuesMap.put(key, font);
  }

  public TextAttributes getAttributes(TextAttributesKey key) {
    migrateFromOldVersions();
    if (myAttributesMap.containsKey(key)) {
      return myAttributesMap.get(key);
    } else {
      return myParentScheme.getAttributes(key);
    }
  }

  public Color getColor(ColorKey key) {
    migrateFromOldVersions();
    if (myColorsMap.containsKey(key)) {
      return myColorsMap.get(key);
    } else {
      return myParentScheme.getColor(key);
    }
  }

  public String getName() {
    return (String)myValuesMap.get(SCHEME_NAME);
  }

  public Object clone() {
    EditorColorsSchemeImpl newScheme = new EditorColorsSchemeImpl(myParentScheme, DefaultColorSchemesManager.getInstance());
    newScheme.myEditorFontSize = myEditorFontSize;
    newScheme.myLineSpacing = myLineSpacing;

    Map newValuesMap = new HashMap(myValuesMap);
    Map<TextAttributesKey,TextAttributes> newAttributesMap = new HashMap<TextAttributesKey, TextAttributes>(myAttributesMap);
    Map<ColorKey,Color> newColorsMap = new HashMap<ColorKey, Color>(myColorsMap);
    newScheme.myValuesMap = newValuesMap;
    newScheme.myAttributesMap = newAttributesMap;
    newScheme.myColorsMap = newColorsMap;

    return newScheme;
  }
}
