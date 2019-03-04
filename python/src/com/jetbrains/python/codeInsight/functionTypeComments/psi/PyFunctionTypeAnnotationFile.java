/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotationFile extends PyFileImpl implements PyExpressionCodeFragment {
  @Nullable private final PsiElement myContext;

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

  @NotNull
  @Override
  public FileType getFileType() {
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

  @Nullable
  public PyFunctionTypeAnnotation getAnnotation() {
    return findChildByClass(PyFunctionTypeAnnotation.class);
  }
}

