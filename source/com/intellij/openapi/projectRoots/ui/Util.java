package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author MYakovlev
 * Date: Oct 29, 2002
 * Time: 8:47:43 PM
 */
public class Util{

  public static VirtualFile showSpecifyJavadocUrlDialog(JComponent parent){
    final String url = Messages.showInputDialog(parent, ProjectBundle.message("sdk.configure.javadoc.url.prompt"),
                                                ProjectBundle.message("sdk.configure.javadoc.url.title"), Messages.getQuestionIcon(), "", new InputValidator() {
      public boolean checkInput(String inputString) {
        return true;
      }
      public boolean canClose(String inputString) {
        try {
          new URL(inputString);
          return true;
        }
        catch (MalformedURLException e1) {
          Messages.showErrorDialog(e1.getMessage(), ProjectBundle.message("sdk.configure.javadoc.url.title"));
        }
        return false;
      }
    });
    if (url == null) {
      return null;
    }
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }


}
