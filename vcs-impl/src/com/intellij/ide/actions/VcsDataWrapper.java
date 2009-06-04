package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class VcsDataWrapper {
  private final Project myProject;
  private final ProjectLevelVcsManager myManager;
  private Map<String, String> myVcses;

  VcsDataWrapper(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    myProject = PlatformDataKeys.PROJECT.getData(dataContext);
    if (myProject == null || myProject.isDefault()) {
      myManager = null;
      myVcses = null;
      return;
    }
    myManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  public boolean enabled() {
    if (myProject == null || myProject.isDefault() || myManager == null) {
      return false;
    }
    if (checkMappings()) {
      return false;
    }
    if (! ((ProjectLevelVcsManagerImpl) myManager).haveVcses()) {
      return false;
    }
    return true;
  }

  private boolean checkMappings() {
    final List<VcsDirectoryMapping> mappings = myManager.getDirectoryMappings();
    for (VcsDirectoryMapping mapping : mappings) {
      final String vcs = mapping.getVcs();
      if (vcs != null && vcs.length() > 0) {
        return true;
      }
    }
    return false;
  }

  public Project getProject() {
    return myProject;
  }

  public ProjectLevelVcsManager getManager() {
    return myManager;
  }

  public Map<String, String> getVcses() {
    if (myVcses == null && myProject != null && !myProject.isDefault()) {
      final AbstractVcs[] allVcss = myManager.getAllVcss();
      myVcses = new HashMap<String, String>(allVcss.length, 1);
      for (AbstractVcs vcs : allVcss) {
        myVcses.put(vcs.getDisplayName(), vcs.getName());
      }
    }
    return myVcses;
  }
}
