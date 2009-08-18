package org.jetbrains.idea.maven.utils;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenGotoSettingsFileContibutor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    Set<String> result = new THashSet<String>();
    for (VirtualFile each : getSettingsFiles(project)) {
      result.add(each.getName());
    }
    return result.toArray(new String[result.size()]);
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    List<NavigationItem> result = new ArrayList<NavigationItem>();
    for (VirtualFile each : getSettingsFiles(project)) {
      if (each.getName().equals(name)) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(each);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return result.toArray(new NavigationItem[result.size()]);
  }

  private List<VirtualFile> getSettingsFiles(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveSettingsFiles();
  }
}