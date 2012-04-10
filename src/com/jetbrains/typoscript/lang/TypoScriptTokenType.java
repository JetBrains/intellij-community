package com.jetbrains.typoscript.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author lene
 *         Date: 03.04.12
 */
public class TypoScriptTokenType extends IElementType {

  public TypoScriptTokenType(@NotNull @NonNls final String debugName) {
    super(debugName, TypoScriptLanguage.INSTANCE);
  }
}
