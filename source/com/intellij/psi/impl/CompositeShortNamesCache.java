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
  private List<PsiShortNamesCache> myCaches = new ArrayList<PsiShortNamesCache>();

  public void addCache(PsiShortNamesCache cache) {
    myCaches.add(cache);
  }

  public void runStartupActivity() {
    for (PsiShortNamesCache cache : myCaches) {
      cache.runStartupActivity();
    }
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    Merger<PsiFile> merger = new Merger<PsiFile>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getFilesByName(name));
    }
    PsiFile[] result = merger.getResult();
    return result != null ? result : PsiFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFileNames() {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getAllFileNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiClass> merger = new Merger<PsiClass>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getClassesByName(name, scope));
    }
    PsiClass[] result = merger.getResult();
    return result != null ? result : PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllClassNames(boolean searchInLibraries) {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getAllClassNames(searchInLibraries));
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllClassNames(boolean searchInLibraries, @NotNull HashSet<String> dest) {
    for (PsiShortNamesCache cache : myCaches) {
      cache.getAllClassNames(searchInLibraries, dest);
    }
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiMethod> merger = new Merger<PsiMethod>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getMethodsByName(name, scope));
    }
    PsiMethod[] result = merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    Merger<PsiMethod> merger = new Merger<PsiMethod>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getMethodsByNameIfNotMoreThan(name, scope, maxCount));
    }
    PsiMethod[] result = merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllMethodNames(boolean searchInLibraries) {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getAllMethodNames(searchInLibraries));
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllMethodNames(boolean searchInLibraries, @NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCaches) {
      cache.getAllMethodNames(searchInLibraries, set);
    }
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Merger<PsiField> merger = new Merger<PsiField>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getFieldsByName(name, scope));
    }
    PsiField[] result = merger.getResult();
    return result != null ? result : PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFieldNames(boolean searchInLibraries) {
    Merger<String> merger = new Merger<String>();
    for (PsiShortNamesCache cache : myCaches) {
      merger.add(cache.getAllFieldNames(searchInLibraries));
    }
    String[] result = merger.getResult();
    return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void getAllFieldNames(boolean searchInLibraries, @NotNull HashSet<String> set) {
    for (PsiShortNamesCache cache : myCaches) {
      cache.getAllFieldNames(searchInLibraries, set);
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
}