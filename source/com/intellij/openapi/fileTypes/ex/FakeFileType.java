/*
 * Created by IntelliJ IDEA.
 * User: valentin
 * Date: 29.01.2004
 * Time: 21:10:56
 */
package com.intellij.openapi.fileTypes.ex;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeModel;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import javax.swing.*;

public abstract class FakeFileType implements FileType {
  public abstract boolean isMyFileType(VirtualFile file);

  public String getDefaultExtension() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public boolean isBinary() {
    return true;
  }

  public boolean isReadOnly() {
    return true;
  }

  public String getCharset(VirtualFile file) {
    return null;
  }

  public PsiFile createPsiFile(VirtualFile file, Project project) {
    return null;
  }

  public PsiFile createPsiFile(Project project, String name, char[] text, int startOffset, int endOffset) {
    return null;
  }

  public FileTypeSupportCapabilities getSupportCapabilities() {
    return null;
  }

  public PseudoTextBuilder getPseudoTextBuilder() {
    return null;
  }

  public StructureViewModel getStructureViewModel(VirtualFile file, Project project) {
    return null;
  }
}