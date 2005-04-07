package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;
import java.util.Iterator;

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
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      return;
    }

    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    final AddToFavoritesAction addToFavoritesAction = favoritesView.getAddToFavoritesAction(myFavoritesName);

    final ArrayList<PsiFile> filesToAdd = getFilesToAdd(project);

    final ArrayList<AbstractTreeNode> nodesToAdd = new ArrayList<AbstractTreeNode>();
    final FavoritesTreeViewConfiguration favoritesConfig = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel(myFavoritesName).getFavoritesTreeStructure().getFavoritesConfiguration();
    for (Iterator<PsiFile> iterator = filesToAdd.iterator(); iterator.hasNext();) {
      //module needs for psi packages only
      AddToFavoritesAction.addPsiElementNode(iterator.next(),
                                             project,
                                             nodesToAdd,
                                             favoritesConfig,
                                             null);
    }
    addToFavoritesAction.addNodes(project, nodesToAdd.toArray(new AbstractTreeNode[nodesToAdd.size()]));
  }

  private ArrayList<PsiFile> getFilesToAdd (Project project) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (int i = 0; i < openFiles.length; i++) {
      VirtualFile openFile = openFiles[i];
      result.add(psiManager.findFile(openFile));
    }
    return result;
  }
}
