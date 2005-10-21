package com.intellij.ui;

import com.intellij.openapi.editor.markup.TextAttributes;

public class HighlightedRegion {
   public int startOffset;
   public int endOffset;
   public TextAttributes textAttributes;

   public HighlightedRegion(int startOffset, int endOffset, TextAttributes textAttributes) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.textAttributes = textAttributes;
   }
}