package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.newCodeFormatting.Wrap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ElementType;

public abstract class WrappingStrategy {

  public static final WrappingStrategy DO_NOT_WRAP = new WrappingStrategy(null) {
    protected boolean shouldWrap(final IElementType type) {
      return false;
    }
  };

  public static WrappingStrategy createDoNotWrapCommaStrategy(Wrap wrap) {
    return new WrappingStrategy(wrap) {
      protected boolean shouldWrap(final IElementType type) {
        return type != ElementType.COMMA;
      }
    };
  }

  private final Wrap myWrap;

  public WrappingStrategy(final Wrap wrap) {
    myWrap = wrap;
  }

  public Wrap getWrap(IElementType type) {
    if (shouldWrap(type)) {
      return myWrap;
    } else {
      return null;
    }
  }

  protected abstract boolean shouldWrap(final IElementType type);
}
