package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.Convertor;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class TextDiffType implements DiffStatusBar.LegendTypeDescriptor {
  public static final TextDiffType INSERT = new TextDiffType(DiffBundle.message("diff.type.inserted.name"), DiffColors.DIFF_INSERTED);
  public static final TextDiffType CHANGED = new TextDiffType(DiffBundle.message("diff.type.changed.name"), DiffColors.DIFF_MODIFIED);
  public static final TextDiffType DELETED = new TextDiffType(DiffBundle.message("diff.type.deleted.name"), DiffColors.DIFF_DELETED);
  public static final TextDiffType CONFLICT = new TextDiffType(DiffBundle.message("diff.type.conflict.name"), DiffColors.DIFF_CONFLICT);

  public static final TextDiffType NONE = new TextDiffType(DiffBundle.message("diff.type.none.name"), null);

  public static final List<TextDiffType> DIFF_TYPES = Arrays.asList(new TextDiffType[]{DELETED, CHANGED, INSERT});
  public static final List<TextDiffType> MERGE_TYPES = Arrays.asList(new TextDiffType[]{DELETED, CHANGED, INSERT, CONFLICT});
  private final TextAttributesKey myAttributesKey;
  private final String myDisplayName;
  public static final Convertor<TextDiffType, TextAttributesKey> ATTRIBUTES_KEY = new Convertor<TextDiffType, TextAttributesKey>() {
    public TextAttributesKey convert(TextDiffType textDiffType) {
      return textDiffType.getAttributesKey();
    }
  };

  private TextDiffType(String displayName, TextAttributesKey attrubutesKey) {
    myAttributesKey = attrubutesKey;
    myDisplayName = displayName;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Color getLegendColor(EditorColorsScheme colorScheme) {
    TextAttributes attributes = getTextAttributes(colorScheme);
    return attributes != null ? attributes.getBackgroundColor() : null;
  }

  public TextAttributesKey getAttributesKey() {
    return myAttributesKey;
  }

  public TextAttributes getTextAttributes(EditorColorsScheme scheme) {
    return scheme.getAttributes(myAttributesKey);
  }

  public Color getPoligonColor(Editor editor1) {
    return getLegendColor(editor1.getColorsScheme());
  }

  public TextAttributes getTextAttributes(Editor editor1) {
    return getTextAttributes(editor1.getColorsScheme());
  }

  public Color getTextBackground(Editor editor) {
    TextAttributes attributes = getTextAttributes(editor);
    return attributes != null ? attributes.getBackgroundColor() : null;
  }

  public String toString(){
    return myDisplayName;
  }
}
