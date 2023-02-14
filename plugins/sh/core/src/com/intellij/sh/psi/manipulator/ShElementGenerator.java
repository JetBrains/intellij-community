// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.manipulator;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.ShFileType;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.ShLiteral;
import org.jetbrains.annotations.NotNull;

public final class ShElementGenerator {
  @NotNull
  public static ShLiteral createLiteral(@NotNull Project project, @NotNull String command) {
    PsiFile file = createTempFile(project, command);
    ShLiteral literal = PsiTreeUtil.findChildOfType(file, ShLiteral.class);
    assert literal != null;
    return literal;
  }

  @NotNull
  public static PsiElement createFunctionIdentifier(@NotNull Project project, @NotNull String functionName) {
    PsiFile file = createTempFile(project, functionName + "() {  }");
    ShFunctionDefinition functionDefinition = PsiTreeUtil.findChildOfType(file, ShFunctionDefinition.class);
    assert functionDefinition != null;
    PsiElement word = functionDefinition.getWord();
    assert word != null;
    return word;
  }

  @NotNull
  private static PsiFile createTempFile(@NotNull Project project, @NotNull String contents) {
    return PsiFileFactory.getInstance(project).createFileFromText("dummy_file." + ShFileType.INSTANCE.getDefaultExtension(),
                                                                  ShFileType.INSTANCE, contents);
  }
}
