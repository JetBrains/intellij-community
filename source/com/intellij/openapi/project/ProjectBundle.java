package com.intellij.openapi.project;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 01.09.2005
 * Time: 18:10:36
 * To change this template use File | Settings | File Templates.
 */
public class ProjectBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.openapi.project.ProjectBundle");

  private ProjectBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.openapi.project.ProjectBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
