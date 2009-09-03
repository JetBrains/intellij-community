package org.jetbrains.yaml;

import com.intellij.CommonBundle;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

/**
 * @author oleg
 */
public class YAMLBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls
  private static final String BUNDLE = "messages.YAMLBundle";

  private YAMLBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  /*
   * This method added for jruby access
   */
  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key) {
    return CommonBundle.message(getBundle(), key);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
