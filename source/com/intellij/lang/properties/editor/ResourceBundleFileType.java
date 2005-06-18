package com.intellij.lang.properties.editor;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author cdr
 */
class ResourceBundleFileType extends FakeFileType {
  public String getName() {
    return "ResourceBundle";
  }

  public String getDescription() {
    return "ResourceBundle fake file type";
  }

  public boolean isMyFileType(VirtualFile file) {
    return file instanceof ResourceBundleAsVirtualFile;
  }

  public SyntaxHighlighter getHighlighter(Project project) {
    return null;
  }
}
