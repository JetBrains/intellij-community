/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;

public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
  private String myPackageName;
  private final boolean myCompiled;

  public PsiJavaFileStubImpl(final PsiJavaFile file, boolean compiled) {
    super(file);
    myPackageName = file.getPackageName();
    myCompiled = compiled;
  }

  public PsiJavaFileStubImpl(final String packageName, boolean compiled) {
    super(null);
    myPackageName = packageName;
    myCompiled = compiled;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public boolean isCompiled() {
    return myCompiled;
  }

  public void setPackageName(final String packageName) {
    myPackageName = packageName;
  }

  public IStubFileElementType getType() {
    return JavaStubElementTypes.FILE;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiJavaFileStub [" + myPackageName + "]";
  }
}