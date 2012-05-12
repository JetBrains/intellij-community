package com.jetbrains.typoscript;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author lene
 *         Date: 12.04.12
 */
public class TypoScriptBundle {
  @NonNls public static String NOTIFICATION_ID = "TypoScript";
  private static Reference<ResourceBundle> ourBundle;

  @NonNls public static final String TYPOSCRIPT_BUNDLE = "messages.TypoScriptBundle";

  private TypoScriptBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = TYPOSCRIPT_BUNDLE) String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(TYPOSCRIPT_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
