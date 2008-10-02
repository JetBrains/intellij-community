/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CompositeShortNamesCache implements PsiShortNamesCache {
  private final List<PsiShortNamesCache> myCaches = new ArrayList<PsiShortNamesCache>();
  private PsiShortNamesCache[] myCacheArray = new PsiShortNamesCache[0];

  public void addCache(PsiShortNamesCache cache) {
    myCaches.add(cache);
    myCacheArray = myCaches.toArray(new PsiShortNamesCache[myCaches.size()]);
  }

  public void runStartupActivity() {
    for (PsiShortNamesCache cache : myCaches) {
      cache.runStartupActivity();
    }
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    Merger<PsiFile> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiFile[] classes = cache.getFilesByName(name);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiFile>();
        merger.add(classes);
      }
    }
    PsiFile[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFileNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllFileNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiClass> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiClass[] classes = cache.getClassesByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiClass>();
        merger.add(classes);
      }
    }
    PsiClass[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllClassNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllClassNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllClassNames(@NotNull HashSet<String> dest) {
    for (PsiShortNamesCache cache : myCacheArray) {
      cache.getAllClassNames(dest);
    }
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiMethod[] classes = cache.getMethodsByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiMethod>();
        merger.add(classes);
      }
    }
    PsiMethod[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    Merger<PsiMethod> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiMethod[] classes = cache.getMethodsByNameIfNotMoreThan(name, scope, maxCount);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiMethod>();
        merger.add(classes);
      }
    }
    PsiMethod[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllMethodNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllMethodNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCacheArray) {
      cache.getAllMethodNames(set);
    }
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiField> merger = null;
    for (PsiShortNamesCache cache : myCacheArray) {
      PsiField[] classes = cache.getFieldsByName(name, scope);
      if (classes.length != 0) {
        if (merger == null) merger = new Merger<PsiField>();
        merger.add(classes);
      }
    }
    PsiField[] result = merger == null ? null : merger.getResult();
    return result != null ? result : PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFieldNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCacheArray) {
      merger.add(cache.getAllFieldNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCacheArray) {
      cache.getAllFieldNames(set);
    }
  }

  private static class Merger<T> {
    private T[] mySingleItem = null;
    private Set<T> myAllItems = null;

    public void add(T[] items) {
      if (items == null || items.length == 0) return;
      if (mySingleItem == null) {
        mySingleItem = items;
        return;
      }
      if (myAllItems == null) {
        myAllItems = new THashSet<T>(Arrays.asList(mySingleItem));
      }
      myAllItems.addAll(Arrays.asList(items));
    }

    public T[] getResult() {
      if (myAllItems == null) return mySingleItem;
      return myAllItems.toArray(mySingleItem);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Override
  public String toString() {
    return "Composite cache: " + myCaches;
  }
}