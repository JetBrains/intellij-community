package com.intellij.testIntegration;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

public class TestFinderHelper {
  public static PsiElement findSourceElement(PsiElement from) {
    for (TestFinder each : getFinders()) {
      PsiElement result = each.findSourceElement(from);
      if (result != null) return result;
    }
    return null;
  }

  public static List<PsiElement> findTestsForClass(PsiElement element) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (TestFinder each : getFinders()) {
      result.addAll(each.findTestsForClass(element));
    }
    return result;
  }

  public static List<PsiElement> findClassesForTest(PsiElement element) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (TestFinder each : getFinders()) {
      result.addAll(each.findClassesForTest(element));
    }
    return result;
  }

  public static boolean isTest(PsiElement element) {
    for (TestFinder each : getFinders()) {
      if (each.isTest(element)) return true;
    }
    return false;
  }

  private static TestFinder[] getFinders() {
    return Extensions.getExtensions(TestFinder.EP_NAME);
  }
}
