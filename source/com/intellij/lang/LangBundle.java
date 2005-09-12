package com.intellij.lang;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.09.2005
 * Time: 18:03:21
 * To change this template use File | Settings | File Templates.
 */
public class LangBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.lang.LangBundle");

  public static String message(@PropertyKey(resourceBundle = "com.intellij.lang.LangBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
