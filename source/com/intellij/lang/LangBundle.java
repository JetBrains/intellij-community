package com.intellij.lang;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.09.2005
 * Time: 18:03:21
 * To change this template use File | Settings | File Templates.
 */
public class LangBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.LangBundle");

  private LangBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.LangBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
