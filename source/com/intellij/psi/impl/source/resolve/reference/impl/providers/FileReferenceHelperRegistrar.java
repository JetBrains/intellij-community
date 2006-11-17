/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peter
 */
public class FileReferenceHelperRegistrar {
  private static LinkedList<FileReferenceHelper> ourHelpers = new LinkedList<FileReferenceHelper>();

  static {
    ourHelpers.add(new PsiFileReferenceHelper());
  }

  public static void registerHelper(FileReferenceHelper helper) {
    ourHelpers.addFirst(helper);
  }

  public static List<FileReferenceHelper> getHelpers() {
    return ourHelpers;
  }

}
