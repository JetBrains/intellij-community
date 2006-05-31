package com.intellij.openapi.fileTypes;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.08.2005
 * Time: 17:41:39
 * To change this template use File | Settings | File Templates.
 */
public class FileTypesBundle {
  @NonNls private static final String BUNDLE = "messages.FileTypesBundle";

  private FileTypesBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
