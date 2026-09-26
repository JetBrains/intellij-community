// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class SvnFormatSelector {

  public static @NotNull WorkingCopyFormat findRootAndGetFormat(@NotNull File path) {
    File root = SvnUtil.getWorkingCopyRoot(path);

<<<<<<< HEAD
    return root != null ? SvnUtil.getFormat(root) : WorkingCopyFormat.UNKNOWN;
=======
    Collection result = null;
    final WorkingCopyFormat presetFormat = SvnWorkingCopyFormatHolder.getPresetFormat();
    if (presetFormat != null) {
      result = format2Factories(presetFormat, factories);
    }

    if (result == null) {
      final WorkingCopyFormat format = getWorkingCopyFormat(path);
      result = format2Factories(format, factories);
    }

    if (result == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY));
    }
    return result;
  }

  @Nullable
  static Collection format2Factories(final WorkingCopyFormat format, final Collection factories) {
    if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      return factories;
    } else if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
      return factoriesFor16(factories);
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

  private static Collection<SVNAdminAreaFactory> factoriesFor16(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<SVNAdminAreaFactory>(2);
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) ||
           (WorkingCopyFormat.ONE_DOT_FIVE.getFormat() == supportedVersion) ||
           (WorkingCopyFormat.ONE_DOT_SIX.getFormat() == supportedVersion)) {
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
    ApplicationManager.getApplication().getMessageBus().syncPublisher(SvnVcs.WC_CONVERTED).run();
    return newMode[0];
  }

  public static WorkingCopyFormat getWorkingCopyFormat(final File path) {
    try {
      final SvnWcGeneration svnWcGeneration = SvnOperationFactory.detectWcGeneration(path, true);
      if (SvnWcGeneration.V17.equals(svnWcGeneration)) return WorkingCopyFormat.ONE_DOT_SEVEN;
    }
    catch (SVNException e) {
      //
    }
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
>>>>>>> origin/115
  }
}
