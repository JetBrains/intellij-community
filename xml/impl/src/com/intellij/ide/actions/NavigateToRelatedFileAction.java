package com.intellij.ide.actions;

import com.intellij.navigation.RelatedFilesContributor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class NavigateToRelatedFileAction extends QuickSwitchSchemeAction {

  public NavigateToRelatedFileAction() {
    super(true);
  }

  @Override
  protected void fillActions(Project project, DefaultActionGroup group, DataContext dataContext) {
    PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
    assert psiFile != null;
    Set<PsiFile> relatedFiles = collectRelatedFiles(psiFile);
    for (PsiFile file : relatedFiles) {
      VirtualFile vFile = file.getVirtualFile();
      if (vFile != null) {
        group.add(new MyAction(file, vFile));
      }
    }
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    PsiFile psiFile = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    boolean visible = false;
    if (psiFile != null) {
      for (RelatedFilesContributor contributor : RelatedFilesContributor.EP_NAME.getExtensions()) {
        if (contributor.isAvailable(psiFile)) {
          visible = true;
          break;
        }
      }
    }
    Presentation presentation = e.getPresentation();
    presentation.setVisible(visible);
    presentation.setEnabled(visible);
  }

  public static Set<PsiFile> collectRelatedFiles(@NotNull PsiFile file) {
    Set<PsiFile> resultSet = new HashSet<PsiFile>();
    for (RelatedFilesContributor contributor : RelatedFilesContributor.EP_NAME.getExtensions()) {
      if (contributor.isAvailable(file)) {
        contributor.fillRelatedFiles(file, resultSet);
      }
    }
    return resultSet;
  }

  private static class MyAction extends AnAction {
    private final PsiFile myFile;

    MyAction(@NotNull PsiFile file, @NotNull VirtualFile virtualFile) {
      super(file.getName(), "Navigate to " + FileUtil.toSystemDependentName(virtualFile.getPath()), file.getFileType().getIcon());
      myFile = file;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myFile.canNavigate()) {
        myFile.navigate(true);
      }
    }
  }
}
