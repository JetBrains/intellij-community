package com.jetbrains.performancePlugin.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.performancePlugin.lang.IJPerfFileType;

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
