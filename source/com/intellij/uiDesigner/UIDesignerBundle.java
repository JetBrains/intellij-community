package com.intellij.uiDesigner;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

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

  public static String message(@PropertyKey String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
