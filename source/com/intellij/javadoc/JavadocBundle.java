package com.intellij.javadoc;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 01.09.2005
 * Time: 14:37:13
 * To change this template use File | Settings | File Templates.
 */
public class JavadocBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.JavadocBundle");

  private JavadocBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.JavadocBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
