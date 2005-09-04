package com.intellij.diagnostic;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.08.2005
 * Time: 18:13:15
 * To change this template use File | Settings | File Templates.
 */
public class DiagnosticBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.diagnostic.DiagnosticBundle");

  private DiagnosticBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.diagnostic.DiagnosticBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
