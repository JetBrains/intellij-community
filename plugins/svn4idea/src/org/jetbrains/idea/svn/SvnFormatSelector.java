package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.peer.PeerFactory;
import org.jetbrains.idea.svn.dialogs.UpgradeFormatDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;

public class SvnFormatSelector implements ISVNAdminAreaFactorySelector {

  public SvnFormatSelector() {
  }

  public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
    if (!writeAccess) {
      return factories;
    }
    // get project for path.
    Project project = findProject(path);
    if (project == null) {
      return factories;
    }
    // get project settings.
    SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    String upgradeMode = configuration.getUpgradeMode();
    return getFactories(upgradeMode, path, factories, project, configuration);
  }

  private static Collection getFactories(String upgradeMode, final File path, Collection factories, final Project project, SvnConfiguration config) {
    if (SvnConfiguration.UPGRADE_NONE.equals(upgradeMode)) {
      // return all factories only if path or its wc root is already in 1.4 format,
      // otherwise return 1.3 factory
      int format  = getWorkingCopyFormat(path);
      if (format == SVNAdminAreaFactory.WC_FORMAT_14) {
        return factories;
      }
      SVNAdminAreaFactory factory = null;
      for(Object f : factories) {
          if (((SVNAdminAreaFactory) f).getSupportedVersion() == SVNAdminAreaFactory.WC_FORMAT_13) {
            factory = (SVNAdminAreaFactory) f;
            break;
          }
      }
      return Collections.singletonList(factory);
    } else if (SvnConfiguration.UPGRADE_AUTO.equals(upgradeMode)) {
      return factories;
    } else if (upgradeMode == null) {
      // ask user and change setting.
      int format = getWorkingCopyFormat(path);
      // no need to ask about upgrade if there is already new format in WC.
      if (format == SVNAdminAreaFactory.WC_FORMAT_14) {
        return factories;
      }
      final String[] newMode = new String[] {null};
      try {
        if (SwingUtilities.isEventDispatchThread()) {
          displayUpgradeDialog(project, path, newMode);
        } else {
          SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
              displayUpgradeDialog(project, path, newMode);
            }
          });
        }
      } catch (InterruptedException e) {
        //
      } catch (InvocationTargetException e) {
        //
      }
      config.setUpgradeMode(newMode[0]);
      return getFactories(newMode[0], path, factories, project, config);
    }
    return factories;
  }

  private static int getWorkingCopyFormat(final File path) {
    int format  = 0;
    try {
      // it is enough to check parent and this.
      format = SVNAdminAreaFactory.checkWC(path, false);
    } catch (SVNException e) {
      // ignore
    }
    if (format == 0 && path.getParentFile() != null) {
      try {
        format = SVNAdminAreaFactory.checkWC(path.getParentFile(), false);
      }
      catch(SVNException e) {
        // ignore
      }
    }
    return format;
  }

  private static void displayUpgradeDialog(Project project, File path, String[] newMode) {
    UpgradeFormatDialog dialog = new UpgradeFormatDialog(project, path, false);
    dialog.show();
    if (dialog.isOK()) {
      newMode[0] = dialog.getUpgradeMode();
    }
  }

  private static Project findProject(final File path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Project>() {
      public Project compute() {
        final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(path);
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
          AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(filePath);
          if (vcs instanceof SvnVcs) {
            return project;
          }
        }
        return null;
      }
    });
  }
}
