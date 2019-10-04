// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.ShFileType;
import com.intellij.sh.psi.ShFunctionName;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShSimpleCommandElement;
import org.jetbrains.annotations.NotNull;

public class ShElementGenerator {
  @NotNull
  public static ShSimpleCommandElement createSimpleCommand(@NotNull Project project, @NotNull String command) {
    PsiFile file = createTempFile(project, "dummy " + command);
    ShSimpleCommandElement simpleCommand = PsiTreeUtil.findChildOfType(file, ShSimpleCommandElement.class);
    assert simpleCommand != null;
    return simpleCommand;
  }

  @NotNull
  public static ShLiteral createLiteral(@NotNull Project project, @NotNull String command) {
    PsiFile file = createTempFile(project, command);
    ShLiteral literal = PsiTreeUtil.findChildOfType(file, ShLiteral.class);
    assert literal != null;
    return literal;
  }

  @NotNull
  public static ShFunctionName createFunctionName(@NotNull Project project, @NotNull String functionName) {
    PsiFile file = createTempFile(project, functionName + "() {  }");
    ShFunctionName functionNameElement = PsiTreeUtil.findChildOfType(file, ShFunctionName.class);
    assert functionNameElement != null;
    return functionNameElement;
  }

  @NotNull
  private static PsiFile createTempFile(@NotNull Project project, @NotNull String contents) {
    return PsiFileFactory.getInstance(project).createFileFromText("dummy_file." + ShFileType.INSTANCE.getDefaultExtension(),
                                                                  ShFileType.INSTANCE, contents);
  }
}
