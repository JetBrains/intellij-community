/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.highlighter;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.codeStyle.java.JavaAdapter;

import javax.swing.*;

public class JavaFileType implements FileType {
  private static final Icon ICON = IconLoader.getIcon("/fileTypes/java.png");

  public String getName() {
    return "JAVA";
  }

  public String getDescription() {
    return "Java source files";
  }

  public String getDefaultExtension() {
    return "java";
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean isBinary() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  public String getCharset(VirtualFile file) {
    return null;
  }

  public FileHighlighter getHighlighter(Project project) {
    LanguageLevel level = project != null ? PsiManager.getInstance(project).getEffectiveLanguageLevel() : LanguageLevel.HIGHEST;
    return new JavaFileHighlighter(level);
  }

  public PsiFile createPsiFile(VirtualFile file, Project project) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInSource(file)) {
      return new PsiJavaFileImpl((PsiManagerImpl)PsiManager.getInstance(project), file);
    }
    return new PsiPlainTextFileImpl((PsiManagerImpl)PsiManager.getInstance(project), file);
  }

  public PsiFile createPsiFile(Project project, String name, char[] text, int startOffset, int endOffset) {
    return new PsiJavaFileImpl((PsiManagerImpl)PsiManager.getInstance(project), name, text, startOffset, endOffset);
  }

  public FileTypeSupportCapabilities getSupportCapabilities() {
    return null;
  }

  public PseudoTextBuilder getPseudoTextBuilder() {
    return new JavaAdapter() {
      protected FileType getFileType() {
        return JavaFileType.this;
      }
    };
  }

  public StructureViewModel getStructureViewModel(VirtualFile file, Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof PsiJavaFile)) return null; 
    return new JavaFileTreeModel((PsiJavaFile)psiFile);
  }
}