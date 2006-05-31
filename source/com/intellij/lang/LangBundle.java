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
  @NonNls private static final String BUNDLE = "messages.LangBundle";

  private LangBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
