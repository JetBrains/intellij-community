package com.intellij.diagnostic;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.08.2005
 * Time: 18:13:15
 * To change this template use File | Settings | File Templates.
 */
public class DiagnosticBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.DiagnosticBundle");

  private DiagnosticBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.DiagnosticBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
