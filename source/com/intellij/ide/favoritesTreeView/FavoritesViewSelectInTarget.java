package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;

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

  protected boolean canSelect(final PsiFileSystemItem file) {
    return findSuitableFavoritesList(file.getVirtualFile(), myProject, null) != null;
  }

  public static String findSuitableFavoritesList(VirtualFile file, Project project, final String currentSubId) {
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    if (currentSubId != null && favoritesManager.contains(currentSubId, file)) return currentSubId;
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

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  public boolean isSubIdSelectable(String subId, VirtualFile file) {
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(myProject);
    return favoritesManager.contains(subId, file);
  }
}
