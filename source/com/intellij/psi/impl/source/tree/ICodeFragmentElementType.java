package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.lang.Language;

/**
 * @author ven
 */
public abstract class ICodeFragmentElementType extends IChameleonElementType {
  public ICodeFragmentElementType(final String debugName, final Language language) {
    super(debugName, language);
  }
}
