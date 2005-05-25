package com.intellij.psi.formatter.java;

import com.intellij.newCodeFormatting.Alignment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ElementType;

public abstract  class AlignmentStrategy {
  private final Alignment myAlignment;
  public static final AlignmentStrategy DO_NOT_ALIGN = new AlignmentStrategy(null) {
    protected boolean shouldAlign(final IElementType type) {
      return false;
    }
  };

  public static final AlignmentStrategy createDoNotAlingCommaStrategy(Alignment alignment) {
    return new AlignmentStrategy(alignment) {
      protected boolean shouldAlign(final IElementType type) {
        return type != ElementType.COMMA || type == null;
      }
    };
  }

  protected AlignmentStrategy(final Alignment alignment) {
    myAlignment = alignment;
  }

  public Alignment getAlignment(IElementType elementType){
    if (shouldAlign(elementType)) {
      return myAlignment;
    } else {
      return null;
    }
  }

  protected abstract boolean shouldAlign(final IElementType type);
}
