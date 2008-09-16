package com.intellij.psi.impl.file;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiJavaDirectoryImpl extends PsiDirectoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiJavaDirectoryImpl");

  public PsiJavaDirectoryImpl(PsiManagerImpl manager, VirtualFile file) {
    super(manager, file);
  }

  protected void updateAddedFile(final PsiFile copyPsi) throws IncorrectOperationException {
    if (copyPsi instanceof PsiFileImpl) {
      ((PsiFileImpl)copyPsi).updateAddedFile();
    }
  }

  public void checkCreateFile(@NotNull final String name) throws IncorrectOperationException {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);
    if (type == StdFileTypes.CLASS) {
      throw new IncorrectOperationException("Cannot create class-file");
    }

    super.checkCreateFile(name);
  }

  public PsiElement add(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      final String name = ((PsiClass)element).getName();
      if (name != null) {
        final PsiClass newClass = JavaDirectoryService.getInstance().createClass(this, name);
        return newClass.replace(element);
      }
      else {
        LOG.error("not implemented");
        return null;
      }
    }
    else {
      return super.add(element);
    }
  }

  public void checkAdd(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      if (element.getParent() instanceof PsiFile) {
        JavaDirectoryServiceImpl.checkCreateClassOrInterface(this, ((PsiClass)element).getName());
      }
      else {
        LOG.error("not implemented");
      }
    }
    else {
      super.checkAdd(element);
    }
  }

  public void navigate(final boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(ProjectViewPane.ID);
    projectView.getProjectViewPaneById(ProjectViewPane.ID).select(this, getVirtualFile(), requestFocus);
    ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW).activate(null);
  }
}
