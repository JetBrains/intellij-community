package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * User: anna
 * Date: Feb 25, 2005
 */
public class FavoritesViewSelectInTarget extends ProjectViewSelectInTarget {
  public FavoritesViewSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.FAVORITES;
  }

  protected boolean canSelect(final PsiFile file) {
    return findSuitableFavoritesList(file.getVirtualFile(), myProject) != null;
  }

  public static String findSuitableFavoritesList(VirtualFile file, Project project){
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    final String[] lists = favoritesManager.getAvailableFavoritesLists();
    for (String name : lists) {
      if (favoritesManager.contains(name, file)) return name;
    }
    return null;
  }

  public String getMinorViewId() {
    return FavoritesProjectViewPane.ID;
  }

  public float getWeight() {
    return StandardTargetWeights.FAVORITES_WEIGHT;
  }

}
