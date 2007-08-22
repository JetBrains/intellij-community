package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author cdr
 */
public class ProjectWizardUtil {
  private ProjectWizardUtil() {
  }

  public static String findNonExistingFileName(String searchDirectory, @NonNls String preferredName, String extension){
    for (int idx = 0; ; idx++){
      final String fileName = (idx > 0? preferredName + idx : preferredName) + extension;
      if(!new File(searchDirectory + File.separator + fileName).exists()) {
        return fileName;
      }
    }
  }

  public static boolean createDirectoryIfNotExists(final String promptPrefix, String directoryPath, boolean promptUser) {
    File dir = new File(directoryPath);
    if (!dir.exists()) {
      if (promptUser) {
        final int answer = Messages.showOkCancelDialog(IdeBundle.message("promot.projectwizard.directory.does.not.exist", promptPrefix,
                                                                         dir.getPath(), ApplicationNamesInfo.getInstance().getProductName()),
                                                       IdeBundle.message("title.directory.does.not.exist"), Messages.getQuestionIcon());
        if (answer != 0) {
          return false;
        }
      }
      final boolean ok = dir.mkdirs();
      if (!ok) {
        Messages.showErrorDialog(IdeBundle.message("error.failed.to.create.directory", dir.getPath()), CommonBundle.getErrorTitle());
        return false;
      }
    }
    return true;
  }
}
