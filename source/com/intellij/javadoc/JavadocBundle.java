package com.intellij.javadoc;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 01.09.2005
 * Time: 14:37:13
 * To change this template use File | Settings | File Templates.
 */
public class JavadocBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.javadoc.JavadocBundle");

  private JavadocBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.javadoc.JavadocBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
