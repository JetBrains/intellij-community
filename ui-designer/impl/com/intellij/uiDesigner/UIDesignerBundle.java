package com.intellij.uiDesigner;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author yole
 */
public class UIDesignerBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.UIDesignerBundle");

  private UIDesignerBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.UIDesignerBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
