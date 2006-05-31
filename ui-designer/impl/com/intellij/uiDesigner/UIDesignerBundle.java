package com.intellij.uiDesigner;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author yole
 */
public class UIDesignerBundle {
  @NonNls private static final String BUNDLE = "messages.UIDesignerBundle";

  private UIDesignerBundle() {}

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
