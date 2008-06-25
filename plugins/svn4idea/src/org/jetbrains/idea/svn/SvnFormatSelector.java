package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.dialogs.SvnMapDialog;
import org.jetbrains.idea.svn.dialogs.UpgradeFormatDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class SvnFormatSelector implements ISVNAdminAreaFactorySelector {

  public SvnFormatSelector() {
  }

  public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
    // get project for path.
    Project project = findProject(path);
    if (project == null) {
      /*final WorkingCopyFormat format = getWorkingCopyFormat(path);
      if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
        return factories;
      } else if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
        return factoriesFor14(factories);
      }
      return factoriesFor13(factories);*/

      String newMode = SvnWorkingCopyFormatHolder.getPresetFormat();
      newMode = (newMode == null) ? SvnWorkingCopyFormatHolder.getRecentlySelected() : newMode;
      while (newMode == null) {
        newMode = showUpgradeDialog(path, null, true, null);
        SvnWorkingCopyFormatHolder.setRecentlySelected(newMode);
      }
      if (SvnConfiguration.UPGRADE_NONE.equals(newMode)) {
        return factoriesFor13(factories);
      } else if (SvnConfiguration.UPGRADE_AUTO.equals(newMode)) {
        return factoriesFor14(factories);
      }
      return factories;
    }
    // get project settings.
    SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    String upgradeMode = configuration.getUpgradeMode();
    return getFactories(upgradeMode, path, factories, project, configuration);
  }

  private static Collection getFactories(String upgradeMode, final File path, Collection factories, final Project project, SvnConfiguration config) {
    final WorkingCopyFormat format = getWorkingCopyFormat(path);

    if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
      SvnWorkingCopyFormatHolder.setRecentlySelected(SvnConfiguration.UPGRADE_AUTO_15);
      return factories;
    }

    if (SvnConfiguration.UPGRADE_NONE.equals(upgradeMode)) {
      if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
        SvnWorkingCopyFormatHolder.setRecentlySelected(SvnConfiguration.UPGRADE_AUTO);
        return factoriesFor14(factories);
      }
      SvnWorkingCopyFormatHolder.setRecentlySelected(SvnConfiguration.UPGRADE_NONE);
      return factoriesFor13(factories);
    } else if (SvnConfiguration.UPGRADE_AUTO_15.equals(upgradeMode)) {
      SvnWorkingCopyFormatHolder.setRecentlySelected(SvnConfiguration.UPGRADE_AUTO_15);
      return factories;
    } else if (SvnConfiguration.UPGRADE_AUTO.equals(upgradeMode)) {
      // 1-3 and 1-4
      SvnWorkingCopyFormatHolder.setRecentlySelected(SvnConfiguration.UPGRADE_AUTO);
      return factoriesFor14(factories);
    } else if (upgradeMode == null) {
      // ask user and change setting.

      final boolean display13format = WorkingCopyFormat.ONE_DOT_THREE.equals(format);
      final String newMode = showUpgradeDialog(path, project, display13format, null);
      config.setUpgradeMode(newMode);
      SvnWorkingCopyFormatHolder.setRecentlySelected(newMode);
      return getFactories(newMode, path, factories, project, config);
    }
    SvnWorkingCopyFormatHolder.setRecentlySelected(SvnConfiguration.UPGRADE_AUTO_15);
    return factories;
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor13(final Collection factories) {
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
      final int supportedVersion = factory.getSupportedVersion();
      if (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) {
        return Collections.singletonList(factory);
      }
    }
    return Collections.emptyList();
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor14(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<SVNAdminAreaFactory>(2);
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion)) {
        result.add(factory);
      }
    }
    return result;
  }

  public static String showUpgradeDialog(final File path, final Project project, final boolean display13format, final String mode) {
    final String[] newMode = new String[] {mode};
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        displayUpgradeDialog(project, path, display13format, newMode);
      } else {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            displayUpgradeDialog(project, path, display13format, newMode);
          }
        });
      }
    } catch (InterruptedException e) {
      //
    } catch (InvocationTargetException e) {
      //
    }
    ApplicationManager.getApplication().getMessageBus().syncPublisher(SvnMapDialog.WC_CONVERTED).run();
    return newMode[0];
  }

  public static WorkingCopyFormat getWorkingCopyFormat(final File path) {
    int format  = 0;
    // it is enough to check parent and this.
    try {
      format = SVNAdminAreaFactory.checkWC(path, false);
    } catch (SVNException e) {
      //
    }
    try {
      if (format == 0 && path.getParentFile() != null) {
        format = SVNAdminAreaFactory.checkWC(path.getParentFile(), false);
      }
    } catch (SVNException e) {
      //
    }

    final WorkingCopyFormat workingCopyFormat = WorkingCopyFormat.getInstance(format);
    /*if (WorkingCopyFormat.UNKNOWN.equals(workingCopyFormat)) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY));
    }*/
    return workingCopyFormat;
  }

  private static void displayUpgradeDialog(Project project, File path, final boolean dispay13format, String[] newMode) {
    UpgradeFormatDialog dialog = new UpgradeFormatDialog(project, path, false);
    dialog.setData(dispay13format, newMode[0]);
    dialog.show();
    if (dialog.isOK()) {
      newMode[0] = dialog.getUpgradeMode();
    }
  }

  private static Project findProject(final File path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Project>() {
      public Project compute() {
        final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(path);
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        // a path that is above WC copy root may be asked = but it still be inside some project
        for (Project project : projects) {
          VirtualFile vFile = ChangesUtil.findValidParent(filePath);
          if (ExcludedFileIndex.getInstance(project).isInContent(vFile)) {
            return project;
          }
        }
        return null;
      }
    });
  }
}
