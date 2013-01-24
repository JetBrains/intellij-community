package com.jetbrains.python.refactoring.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapDescriptorBase;
import com.intellij.codeInsight.unwrap.Unwrapper;

/**
 * user : ktisha
 */
public class PyUnwrapDescriptor extends UnwrapDescriptorBase{
  @Override
  protected Unwrapper[] createUnwrappers() {
    return new Unwrapper[] {
      new PyIfUnwrapper(),
      new PyWhileUnwrapper(),
      new PyElseRemover(),
      new PyElseUnwrapper(),
      new PyElIfUnwrapper(),
      new PyElIfRemover(),
      new PyTryUnwrapper(),
      new PyForUnwrapper(),
      new PyWithUnwrapper()
    };
  }
}
