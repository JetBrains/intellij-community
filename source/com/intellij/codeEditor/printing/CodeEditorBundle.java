package com.intellij.codeEditor.printing;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
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
  @NonNls private static final String BUNDLE = "messages.CodeEditorBundle";

  private CodeEditorBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
