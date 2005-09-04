package com.intellij.codeEditor.printing;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

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

  public static String message(@PropertyKey(resourceBundle = "com.intellij.codeEditor.printing.CodeEditorBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
