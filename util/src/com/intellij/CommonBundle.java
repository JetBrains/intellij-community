package com.intellij;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.08.2005
 * Time: 18:06:12
 * To change this template use File | Settings | File Templates.
 */
public class CommonBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.CommonBundle");

  private CommonBundle() {}

  public static String message(String key, Object... params) {
    return message(ourBundle, key, params);
  }

  public static String message(final ResourceBundle bundle, final String key, final Object... params) {
    String value;
    try {

      value = bundle.getString(key);
    }
    catch (MissingResourceException e) {
      return "!" + key + "!";
    }

    if (params.length > 0) {
      return MessageFormat.format(value, params);
    }

    return value;
  }

  public static String getCancelButtonText() {
    return message("button.cancel");
  }

  public static String getErrorTitle() {
    return message("title.error");
  }

  public static String getLoadingTreeNodeText() {
    return CommonBundle.message("tree.node.loading");
  }

  public static String getOkButtonText(){
    return message("button.ok");
  }

  public static String getYesButtonText(){
    return CommonBundle.message("button.yes");
  }

  public static String getNoButtonText(){
    return CommonBundle.message("button.no");
  }

  public static String getContinueButtonText(){
    return CommonBundle.message("button.continue");
  }


  public static String getYesForAllButtonText() {
    return CommonBundle.message("button.yes.for.all");
  }

  public static String getCloseButtonText() {
    return "Close";
  }
}
