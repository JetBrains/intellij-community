package org.jetbrains.idea.maven.vfs;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;

public class MavenGotoPropertyFileContibutor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    if (!includeNonProjectItems) return ArrayUtil.EMPTY_STRING_ARRAY;
    return MavenPropertiesVirtualFileSystem.PROPERTIES_FILES;
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    VirtualFile file = MavenPropertiesVirtualFileSystem.getInstance().findFileByPath(name);
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) return new NavigationItem[]{psiFile};
    }
    return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY;
  }
}
