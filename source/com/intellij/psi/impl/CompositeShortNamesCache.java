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
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CompositeShortNamesCache implements PsiShortNamesCache {
  private List<PsiShortNamesCache> myCaches = new ArrayList<PsiShortNamesCache>();
  private static final String[] EMPTY_STRINGS = new String[0];

  public void addCache(PsiShortNamesCache cache) {
    myCaches.add(cache);
  }

  public void runStartupActivity() {
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      cache.runStartupActivity();
    }
  }

  public PsiFile[] getFilesByName(String name) {
    Merger<PsiFile> merger = new Merger<PsiFile>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getFilesByName(name));
    }
    PsiFile[] result = merger.getResult();
    return result != null ? result : PsiFile.EMPTY_ARRAY;
  }

  public String[] getAllFileNames() {
    Merger<String> merger = new Merger<String>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getAllFileNames());
    }
    String[] result = merger.getResult();
    return result != null ? result : EMPTY_STRINGS;
  }

  public PsiClass[] getClassesByName(String name, GlobalSearchScope scope) {
    Merger<PsiClass> merger = new Merger<PsiClass>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getClassesByName(name, scope));
    }
    PsiClass[] result = merger.getResult();
    return result != null ? result : PsiClass.EMPTY_ARRAY;
  }

  public String[] getAllClassNames(boolean searchInLibraries) {
    Merger<String> merger = new Merger<String>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getAllClassNames(searchInLibraries));
    }
    String[] result = merger.getResult();
    return result != null ? result : EMPTY_STRINGS;
  }

  public void getAllClassNames(boolean searchInLibraries, HashSet<String> dest) {
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      cache.getAllClassNames(searchInLibraries, dest);
    }
  }

  public PsiMethod[] getMethodsByName(String name, GlobalSearchScope scope) {
    Merger<PsiMethod> merger = new Merger<PsiMethod>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getMethodsByName(name, scope));
    }
    PsiMethod[] result = merger.getResult();
    return result != null ? result : PsiMethod.EMPTY_ARRAY;
  }

  public String[] getAllMethodNames(boolean searchInLibraries) {
    Merger<String> merger = new Merger<String>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getAllMethodNames(searchInLibraries));
    }
    String[] result = merger.getResult();
    return result != null ? result : EMPTY_STRINGS;
  }

  public void getAllMethodNames(boolean searchInLibraries, HashSet<String> set) {
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      cache.getAllMethodNames(searchInLibraries, set);
    }
  }

  public PsiField[] getFieldsByName(String name, GlobalSearchScope scope) {
    Merger<PsiField> merger = new Merger<PsiField>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getFieldsByName(name, scope));
    }
    PsiField[] result = merger.getResult();
    return result != null ? result : PsiField.EMPTY_ARRAY;
  }

  public String[] getAllFieldNames(boolean searchInLibraries) {
    Merger<String> merger = new Merger<String>();
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      merger.add(cache.getAllFieldNames(searchInLibraries));
    }
    String[] result = merger.getResult();
    return result != null ? result : EMPTY_STRINGS;
  }

  public void getAllFieldNames(boolean checkBoxState, HashSet<String> set) {
    for (int i = 0; i < myCaches.size(); i++) {
      PsiShortNamesCache cache = myCaches.get(i);
      cache.getAllFieldNames(checkBoxState, set);
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