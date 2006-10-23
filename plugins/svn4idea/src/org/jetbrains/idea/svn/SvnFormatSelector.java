package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
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

  private Collection getFactories(String upgradeMode, final File path, Collection factories, final Project project, SvnConfiguration config) {
    if (SvnConfiguration.UPGRADE_NONE.equals(upgradeMode)) {
      // return all factories only if path or its wc root is already in 1.4 format,
      // otherwise return 1.3 factory
      int format  = 0;
      try {
        // it is enough to check parent and this.
        format = SVNAdminAreaFactory.checkWC(path, false);
        if (format == 0 && path.getParentFile() != null) {
          format = SVNAdminAreaFactory.checkWC(path.getParentFile(), false);
        }
      } catch (SVNException e) {
        //
      }
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
      int format  = 0;
      try {
        // it is enough to check parent and this.
        format = SVNAdminAreaFactory.checkWC(path, false);
        if (format == 0 && path.getParentFile() != null) {
          format = SVNAdminAreaFactory.checkWC(path.getParentFile(), false);
        }
      } catch (SVNException e) {
        //
      }
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

  private void displayUpgradeDialog(Project project, File path, String[] newMode) {
    UpgradeFormatDialog dialog = new UpgradeFormatDialog(project, path, false);
    dialog.show();
    if (dialog.isOK()) {
      newMode[0] = dialog.getUpgradeMode();
    }
  }

  private static Project findProject(final File path) {
    final Project[] p = new Project[1];
    if (SwingUtilities.isEventDispatchThread()) {
      doFindProject(path, p);
    } else {
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            doFindProject(path, p);
          }
        });
      } catch (InterruptedException e) {
        //
      } catch (InvocationTargetException e) {
        //
      }
    }
    return p[0];
  }

  private static void doFindProject(final File path, final Project[] p) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        String filePath = path.getAbsoluteFile().getAbsolutePath().replace(File.separatorChar, '/');
        for (Project project : projects) {
          VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
          for (VirtualFile root : roots) {
            String rootPath = root.getPath();
            if (rootPath != null && (filePath.equals(rootPath) || filePath.startsWith(rootPath + '/'))) {
              p[0] = project;
              return;
            }
          }
        }
        p[0] = ProjectManager.getInstance().getDefaultProject();
      }
    });
  }
}
