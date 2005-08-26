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
}
