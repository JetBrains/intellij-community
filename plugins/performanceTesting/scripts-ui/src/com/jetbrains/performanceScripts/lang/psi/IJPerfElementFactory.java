// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.performanceScripts.lang.IJPerfFileType;

public final class IJPerfElementFactory {
  public static IJPerfCommandName createCommandName(Project project, String name) {
    final IJPerfFile file = createFile(project, name);
    return (IJPerfCommandName)file.getFirstChild();
  }

  private static IJPerfFile createFile(Project project, String text) {
    String name = "dummy.ijperf";
    return (IJPerfFile)PsiFileFactory.getInstance(project).createFileFromText(name, IJPerfFileType.INSTANCE, text);
  }
}
