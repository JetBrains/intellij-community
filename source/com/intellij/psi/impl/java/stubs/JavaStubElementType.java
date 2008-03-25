/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class JavaStubElementType<StubT extends StubElement, PsiT extends PsiElement> extends IStubElementType<StubT, PsiT> {
  public JavaStubElementType(@NotNull @NonNls final String debugName) {
    super(debugName, StdFileTypes.JAVA != null ? StdFileTypes.JAVA.getLanguage() : null);
  }
}