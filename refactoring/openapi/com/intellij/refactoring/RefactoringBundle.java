package com.intellij.refactoring;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author ven
 */
public class RefactoringBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls private static final String BUNDLE = "messages.RefactoringBundle";

  private RefactoringBundle() {
  }

  public static String getSearchInCommentsAndStringsText() {
    return message("search.in.comments.and.strings");
  }

  public static String getSearchForTextOccurrencesText() {
    return message("search.for.text.occurrences");
  }

  public static String getVisibilityPackageLocal() {
    return message("visibility.package.local");
  }

  public static String getVisibilityPrivate() {
    return message("visibility.private");
  }

  public static String getVisibilityProtected() {
    return message("visibility.protected");
  }

  public static String getVisibilityPublic() {
    return message("visibility.public");
  }

  public static String getVisibilityAsIs() {
    return message("visibility.as.is");
  }

  public static String getEscalateVisibility() {
    return message("visibility.escalate");
  }

  public static String getCannotRefactorMessage(final String message) {
    return message("cannot.perform.refactoring") + "\n" + message;
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
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
