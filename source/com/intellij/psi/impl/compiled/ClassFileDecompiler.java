/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;

public class ClassFileDecompiler implements BinaryFileDecompiler {
  public CharSequence decompile(final VirtualFile file) {
    assert file.getFileType() == StdFileTypes.CLASS;

    final Project project;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      project = ((ProjectManagerEx)ProjectManager.getInstance()).getCurrentTestProject();
      assert project != null;
    }
    else {
      final Project[] projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) return "";
      project = projects[0];
    }

    return ClsFileImpl.decompile(PsiManager.getInstance(project), file);
  }
}