package com.intellij.find;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
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
  @NonNls private static final String BUNDLE = "messages.FindBundle";

  private FindBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
