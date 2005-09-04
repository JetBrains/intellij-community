package com.intellij.tools;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.09.2005
 * Time: 16:41:11
 * To change this template use File | Settings | File Templates.
 */
public class ToolsBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.tools.ToolsBundle");

  private ToolsBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.tools.ToolsBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
