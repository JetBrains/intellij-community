package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 * User: anna
 * Date: Feb 25, 2005
 */
public class FavoritesViewSelectInTarget extends SelectInTargetPsiWrapper{
  public FavoritesViewSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.FAVORITES;
  }

  protected boolean canSelect(final PsiFile file) {
    return findSuitableFavoritesList(file.getVirtualFile()) != null;
  }

  protected void select(PsiElement element, boolean requestFocus) {
    while (true) {
      if (element instanceof PsiFile) {
        break;
      }
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) {
        break;
      }
      element = element.getParent();
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }

    final PsiElement _element = element.getOriginalElement();

    selectElement(new Runnable() {
      public void run() {
        final VirtualFile virtualFile = BasePsiNode.getVirtualFile(_element);
        final FavoritesTreeViewPanel suitableFavoritesList = findSuitableFavoritesList(virtualFile);
        if (suitableFavoritesList != null){
          suitableFavoritesList.selectElement(_element, virtualFile);
          final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
          windowManager.getToolWindow(ToolWindowId.FAVORITES_VIEW).activate(null);
          final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(myProject);
          favoritesView.setSelectedContent(favoritesView.getContent(suitableFavoritesList));
        }
      }
    }, requestFocus);
  }

  private void selectElement(final Runnable runnable, final boolean requestFocus) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.FAVORITES_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  private FavoritesTreeViewPanel findSuitableFavoritesList(VirtualFile file){
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(myProject);
    final String[] allAddActionNames = favoritesView.getAvailableFavoritesLists();
    for (int i = 0; i < allAddActionNames.length; i++) {
      String actionName = allAddActionNames[i];
      final FavoritesTreeViewPanel favoritesTreeViewPanel = favoritesView.getFavoritesTreeViewPanel(actionName);
      if (favoritesTreeViewPanel.getFavoritesTreeStructure().contains(file)){
        return favoritesTreeViewPanel;
      }
    }
    return null;
  }

  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectElement(new Runnable() {
      public void run() {
        if (virtualFile == null) return;
        final FavoritesTreeViewPanel suitableFavoritesList = findSuitableFavoritesList(virtualFile);
        if (suitableFavoritesList != null){
          suitableFavoritesList.selectElement(selector, virtualFile);
          final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
          windowManager.getToolWindow(ToolWindowId.FAVORITES_VIEW).activate(null);
          final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(myProject);
          favoritesView.setSelectedContent(favoritesView.getContent(suitableFavoritesList));
        }
      }
    }, requestFocus);
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  public String getToolWindowId() {
    return ToolWindowId.FAVORITES_VIEW;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return StandardTargetWeights.FAVORITES_WEIGHT;
  }
}
