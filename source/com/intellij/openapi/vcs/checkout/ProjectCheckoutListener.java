package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import org.apache.oro.io.GlobFilenameFilter;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author yole
 */
public class ProjectCheckoutListener implements CheckoutListener {
  public boolean processCheckedOutDirectory(Project project, File directory) {
    //noinspection HardCodedStringLiteral
    File[] files = directory.listFiles((FilenameFilter) new GlobFilenameFilter("*.ipr"));
    if (files != null && files.length > 0) {
      int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.open.project.prompt", files[0].getAbsolutePath()),
                                        VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
      if (rc == 0) {
        ProjectUtil.openProject(files [0].getAbsolutePath(), project, false);
      }
      return true;
    }
    return false;
  }
}