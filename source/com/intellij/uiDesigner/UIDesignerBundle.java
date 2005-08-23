package com.intellij.uiDesigner;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 16.08.2005
 * Time: 16:20:32
 * To change this template use File | Settings | File Templates.
 */
public class UIDesignerBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.uiDesigner.UIDesignerBundle");

  private UIDesignerBundle() {}

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
