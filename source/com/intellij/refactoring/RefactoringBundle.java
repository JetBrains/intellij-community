package com.intellij.refactoring;

import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

import com.intellij.CommonBundle;

/**
 * @author ven
 */
public class RefactoringBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.refactoring.RefactoringBundle");

  private RefactoringBundle() {}

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }

  public static String getSearchInCommentsAndStringsText() {
    return message("search.in.comments.and.strings");
  }

  public static String getSearchForTextOccurencesText() {
    return message("search.for.text.occurences");
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

  public static String getCannotPerformRefactoringMessage(final String message) {
    return message("cannot.perform.refactoring")  + "\n" + message;
  }
}
