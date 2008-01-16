/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public class ClassFileViewProvider extends SingleRootFileViewProvider {
  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile file) {
    super(manager, file);
  }

  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile virtualFile, final boolean physical) {
    super(manager, virtualFile, physical);
  }

  @Override
  protected PsiFile creatFile(final Project project, final VirtualFile vFile, final FileType fileType) {
    if (ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
      String name = vFile.getName();

      // skip inners & anonymous
      int dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) dotIndex = name.length();
      int index = name.lastIndexOf('$', dotIndex);
      if (index >= 0) return null;

      return new ClsFileImpl((PsiManagerImpl)PsiManager.getInstance(project), this);
    }
    return null;
  }

  public FileViewProvider clone() {
    final VirtualFile origFile = getVirtualFile();
    LightVirtualFile copy = new LightVirtualFile(origFile.getName(), origFile.getFileType(), getContents(), getModificationStamp());
    return new ClassFileViewProvider(getManager(), copy, false);
  }
}