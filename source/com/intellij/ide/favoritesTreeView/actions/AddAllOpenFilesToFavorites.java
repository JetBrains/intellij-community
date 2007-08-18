package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;

/**
 * User: anna
 * Date: Apr 5, 2005
 */
public class AddAllOpenFilesToFavorites extends AnAction{
  private String myFavoritesName;
  public AddAllOpenFilesToFavorites(String choosenList) {
    super(choosenList);
    myFavoritesName = choosenList;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());
    if (project == null){
      return;
    }

    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);

    final ArrayList<PsiFile> filesToAdd = getFilesToAdd(project);
    for (PsiFile file : filesToAdd) {
      favoritesManager.addRoots(myFavoritesName, null, file);
    }
  }

  static ArrayList<PsiFile> getFilesToAdd (Project project) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      result.add(psiManager.findFile(openFile));
    }
    return result;
  }

  public void update(AnActionEvent e) {
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(!getFilesToAdd(project).isEmpty());
  }
}
