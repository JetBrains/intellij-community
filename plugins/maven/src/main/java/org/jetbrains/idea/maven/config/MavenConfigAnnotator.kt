// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

class MavenConfigAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is PsiPlainTextFileImpl) {
      val elementFile = element.containingFile.virtualFile
      if (!isConfigFile(elementFile)) {
        return
      }
      val manager = MavenProjectsManager.getInstance(element.getProject())
      if (!manager.isMavenizedProject) {
        return
      }
      val mavenProject = manager.getRootProjects().firstOrNull { it.directoryFile == elementFile.parent?.parent } ?: return;
      val error = mavenProject.configFileError ?: return;
      holder.createErrorAnnotation(element, error)
    }
  }

  private fun isConfigFile(file: VirtualFile?): Boolean {
    val parent = file?.parent ?: return false
    return file.name == "maven.config" && parent.name == ".mvn"
  }
}