package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.08.2005
 * Time: 17:41:39
 * To change this template use File | Settings | File Templates.
 */
public class FileTypesBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.openapi.fileTypes.FileTypesBundle");

  private FileTypesBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.openapi.fileTypes.FileTypesBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
