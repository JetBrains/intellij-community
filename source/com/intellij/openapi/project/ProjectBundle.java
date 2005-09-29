package com.intellij.openapi.project;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 01.09.2005
 * Time: 18:10:36
 * To change this template use File | Settings | File Templates.
 */
public class ProjectBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.ProjectBundle");

  private ProjectBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.ProjectBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
