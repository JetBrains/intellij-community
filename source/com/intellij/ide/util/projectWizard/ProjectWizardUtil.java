package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.ui.Messages;

import java.io.File;

/**
 * @author cdr
 */
public class ProjectWizardUtil {
  public static String findNonExistingFileName(String searchDirectory, String preferredName, String extension){
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
        final int answer = Messages.showOkCancelDialog(promptPrefix + "\"" + dir.getPath() + "\"\ndoes not exist. It will be created by IDEA.", "Directory Does Not Exist", Messages.getQuestionIcon());
        if (answer != 0) {
          return false;
        }
      }
      final boolean ok = dir.mkdirs();
      if (!ok) {
        Messages.showErrorDialog("Failed to create directory \"" + dir.getPath() + "\"", "Error");
        return false;
      }
    }
    return true;
  }
}
