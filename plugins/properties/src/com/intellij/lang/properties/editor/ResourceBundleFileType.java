package com.intellij.lang.properties.editor;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.properties.PropertiesBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class ResourceBundleFileType extends FakeFileType {
  @NotNull
  public String getName() {
    return "ResourceBundle";
  }

  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("resourcebundle.fake.file.type.description");
  }

  public boolean isMyFileType(VirtualFile file) {
    return file instanceof ResourceBundleAsVirtualFile;
  }

  public SyntaxHighlighter getHighlighter(Project project, final VirtualFile virtualFile) {
    return null;
  }
}
