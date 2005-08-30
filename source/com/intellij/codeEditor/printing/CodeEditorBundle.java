package com.intellij.codeEditor.printing;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.08.2005
 * Time: 18:49:13
 * To change this template use File | Settings | File Templates.
 */
public class CodeEditorBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.codeEditor.printing.CodeEditorBundle");

  private CodeEditorBundle() {}

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
