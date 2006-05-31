package com.intellij.tools;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.09.2005
 * Time: 16:41:11
 * To change this template use File | Settings | File Templates.
 */
public class ToolsBundle {
  @NonNls private static final String BUNDLE = "messages.ToolsBundle";

  private ToolsBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
