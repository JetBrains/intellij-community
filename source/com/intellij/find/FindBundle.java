package com.intellij.find;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.08.2005
 * Time: 13:20:15
 * To change this template use File | Settings | File Templates.
 */
public class FindBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.find.FindBundle");

  private FindBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.find.FindBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
