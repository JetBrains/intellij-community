/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class EmptyShortNamesCacheImpl implements PsiShortNamesCache {
  public void runStartupActivity() {
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return PsiFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFileNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllClassNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllClassNames(@NotNull HashSet<String> dest) {
    // do nothing
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllMethodNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    // do nothing
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFieldNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    // do nothing
  }
}