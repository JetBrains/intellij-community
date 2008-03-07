package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public class PyModuleType implements PyType {
  private PsiFile myModule;

  public PyModuleType(final PsiFile module) {
    myModule = module;
  }

  public PsiFile getModule() {
    return myModule;
  }
}
