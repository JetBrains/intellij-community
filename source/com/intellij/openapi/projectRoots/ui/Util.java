package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
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
    final String url = Messages.showInputDialog(parent, "Enter javadoc URL:", "Specify Javadoc URL", Messages.getQuestionIcon(), "", new InputValidator() {
      public boolean checkInput(String inputString) {
        return true;
      }
      public boolean canClose(String inputString) {
        try {
          new URL(StringUtil.endsWithChar(inputString, '/') ? inputString : (inputString + "/"));
          return true;
        }
        catch (MalformedURLException e1) {
          Messages.showErrorDialog(e1.getMessage(), "Specify Javadoc URL");
        }
        return false;
      }
    });
    if (url == null) {
      return null;
    }
    return VirtualFileManager.getInstance().findFileByUrl(StringUtil.endsWithChar(url, '/') ? url : (url + "/"));
  }


}
