/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ClassMap;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peter
 */
public class FileReferenceHelperRegistrar {
  private static final ClassMap<FileReferenceHelper> ourHelpersMap = new ClassMap<FileReferenceHelper>();
  private static final LinkedList<FileReferenceHelper> ourHelpers = new LinkedList<FileReferenceHelper>();

  static {
    registerHelper(new PsiFileReferenceHelper());
  }

  public static void registerHelper(FileReferenceHelper helper) {
    ourHelpers.addFirst(helper);
    ourHelpersMap.put(helper.getDirectoryClass(), helper);
  }

  public static List<FileReferenceHelper> getHelpers() {
    return ourHelpers;
  }

  @Nullable
  public static <T extends PsiFileSystemItem> FileReferenceHelper<T> getHelper(T psiFileSystemItem) {
    return (FileReferenceHelper<T>)getHelper(psiFileSystemItem.getClass());
  }

  @Nullable
  public static <T extends PsiFileSystemItem> FileReferenceHelper<T> getHelper(Class<T> directoryClass) {
    return ourHelpersMap.get(directoryClass);
  }

}
