/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClsStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull final FileType fileType, @NotNull final VirtualFile file, @NotNull final Project project) {
    final PsiJavaFile javaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(file);
    if (javaFile == null) return null;
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new JavaFileTreeModel(javaFile);
      }
    };
  }
}