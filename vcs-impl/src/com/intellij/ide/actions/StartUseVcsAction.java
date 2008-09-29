package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StartUseVcsAction extends AnAction {
  @Override
  public void update(final AnActionEvent e) {
    final Data data = new Data(e);
    final boolean enabled = data.enabled();

    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
    if (enabled) {
      presentation.setText(VcsBundle.message("action.enable.version.control.integration.text"));
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Data data = new Data(e);
    final boolean enabled = data.enabled();
    if (! enabled) {
      return;
    }

    final StartUseVcsDialog dialog = new StartUseVcsDialog(data);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final String vcsName = dialog.getVcs();
      if (vcsName.length() > 0) {
        data.getManager().setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping("", vcsName)));
      }
    }
  }

  static class Data {
    private final Project myProject;
    private final ProjectLevelVcsManager myManager;
    private final Map<String, String> myVcses;

    Data(final AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      myProject = PlatformDataKeys.PROJECT.getData(dataContext);
      if (myProject == null || myProject.isDefault()) {
        myManager = null;
        myVcses = null;
        return;
      }
      myManager = ProjectLevelVcsManager.getInstance(myProject);
      final AbstractVcs[] allVcss = myManager.getAllVcss();
      myVcses = new HashMap<String, String>(allVcss.length, 1);
      for (AbstractVcs vcs : allVcss) {
        myVcses.put(vcs.getDisplayName(), vcs.getName());
      }
    }

    boolean enabled() {
      if (myProject == null || myManager == null || myVcses == null) {
        return false;
      }
      if (checkMappings()) {
        return false;
      }
      if (myVcses.isEmpty()) {
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
      return myVcses;
    }
  }
}
