package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;

public enum AnnotationSource {
  LOCAL() {
    public ColorKey getColor() {
      return EditorColors.ANNOTATIONS_COLOR;
    }
    public boolean showMerged() {
      return false;
    }},
  MERGE() {
    public ColorKey getColor() {
      return EditorColors.ANNOTATIONS_MERGED_COLOR;
    }
    public boolean showMerged() {
      return true;
    }};

  public abstract boolean showMerged();
  public abstract ColorKey getColor();

  public static AnnotationSource getInstance(final boolean showMerged) {
    return showMerged ? MERGE : LOCAL;
  }
}
