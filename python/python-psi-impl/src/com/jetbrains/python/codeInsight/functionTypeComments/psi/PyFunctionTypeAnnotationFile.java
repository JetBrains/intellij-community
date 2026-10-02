// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.functionTypeComments.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationFileType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public class PyFunctionTypeAnnotationFile extends PyFileImpl implements PyExpressionCodeFragment {
  private final @Nullable PsiElement myContext;

  public PyFunctionTypeAnnotationFile(FileViewProvider viewProvider) {
    super(viewProvider, PyFunctionTypeAnnotationDialect.INSTANCE);
    myContext = null;
  }

  public PyFunctionTypeAnnotationFile(@NotNull String text, @NotNull PsiElement context) {
    super(PsiManagerEx.getInstanceEx(context.getProject())
            .getFileManager()
            .createFileViewProvider(new LightVirtualFile("foo.bar", PyFunctionTypeAnnotationFileType.INSTANCE, text),
                                    false));
    myContext = context;
  }

  @Override
  public @NotNull FileType getFileType() {
    return PyFunctionTypeAnnotationFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "FunctionTypeComment:" + getName();
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    // The same as for .pyi files
    return LanguageLevel.getLatest();
  }

  @Override
  public PsiElement getContext() {
    return myContext != null && myContext.isValid() ? myContext : super.getContext();
  }

  public @Nullable PyFunctionTypeAnnotation getAnnotation() {
    return findChildByClass(PyFunctionTypeAnnotation.class);
  }
}

