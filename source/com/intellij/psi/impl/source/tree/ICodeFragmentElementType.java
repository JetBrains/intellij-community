package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IChameleonElementType;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public abstract class ICodeFragmentElementType extends IChameleonElementType {
  public ICodeFragmentElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }
}
