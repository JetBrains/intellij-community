package com.intellij.psi.tree;

import com.intellij.lang.Language;

public abstract class IErrorCounterChameleonElementType extends IChameleonElementType{
  public static final int NO_ERRORS = 0;
  public static final int FATAL_ERROR = Integer.MIN_VALUE;

  public IErrorCounterChameleonElementType(final String debugName, final Language language) {
    super(debugName, language);
  }

  public abstract int getErrorsCount(CharSequence seq);

  public boolean isParsable(CharSequence buffer) {
    return getErrorsCount(buffer) == NO_ERRORS;
  }
}
