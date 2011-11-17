/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.SvnMapDialog;
import org.jetbrains.idea.svn.dialogs.UpgradeFormatDialog;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class SvnFormatSelector implements ISVNAdminAreaFactorySelector {
  public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return factories;
    }

    if (! writeAccess) {
      return factories;
    }

    final WorkingCopyFormat format = getWorkingCopyFormat(path);
    Collection result = format2Factories(format, factories);

    if (result == null) {
      final WorkingCopyFormat presetFormat = SvnWorkingCopyFormatHolder.getPresetFormat();
      if (presetFormat != null) {
        result = format2Factories(presetFormat, factories);
      }
    }

    if (result == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY));
    }
    return result;
  }

  @Nullable
  static Collection format2Factories(final WorkingCopyFormat format, final Collection factories) {
    if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
      return factories;
    } else if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
      return factoriesFor15(factories);
    } else if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
      return factoriesFor14(factories);
    } else if (WorkingCopyFormat.ONE_DOT_THREE.equals(format)) {
      return factoriesFor13(factories);
    }
    return null;
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

  private static Collection<SVNAdminAreaFactory> factoriesFor15(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<SVNAdminAreaFactory>(2);
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) ||
           (WorkingCopyFormat.ONE_DOT_FIVE.getFormat() == supportedVersion)) {
        result.add(factory);
      }
    }
    return result;
  }

  public static String showUpgradeDialog(final File path, final Project project, final boolean display13format, final String mode,
                                         @NotNull final Ref<Boolean> wasOk) {
    assert ! ApplicationManager.getApplication().isUnitTestMode();
    final String[] newMode = new String[] {mode};
    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(new Runnable() {
      public void run() {
        wasOk.set(displayUpgradeDialog(project, path, display13format, newMode));
      }
    });
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

    return WorkingCopyFormat.getInstance(format);
  }

  private static boolean displayUpgradeDialog(Project project, File path, final boolean dispay13format, String[] newMode) {
    UpgradeFormatDialog dialog = new UpgradeFormatDialog(project, path, false);
    dialog.setData(dispay13format, newMode[0]);
    dialog.show();
    if (dialog.isOK()) {
      newMode[0] = dialog.getUpgradeMode();
    }
    return dialog.isOK();
  }
}
