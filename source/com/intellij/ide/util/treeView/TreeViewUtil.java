/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.treeView;

import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 6, 2004
 */
public class TreeViewUtil {

  public static String calcAbbreviatedPackageFQName(PsiPackage aPackage) {

    class Abbreviator {
      static final int SUBPACKAGE_LIMIT = 2;
      boolean shouldAbbreviateName(PsiPackage p) {return scanPackages(p, 1);}
      boolean scanPackages(PsiPackage p, int packageNameOccurrencesFound) {
        final PsiPackage[] subPackages = p.getSubPackages();
        packageNameOccurrencesFound += subPackages.length;
        if (packageNameOccurrencesFound > SUBPACKAGE_LIMIT) {
          return true;
        }
        for (int idx = 0; idx < subPackages.length; idx++) {
          if (scanPackages(subPackages[idx], packageNameOccurrencesFound)) {
            return true;
          }
        }
        return false;
      }
    };

    final Abbreviator abbreviator = new Abbreviator();
    final StringBuffer name = new StringBuffer(aPackage.getName());
    for (PsiPackage parentPackage = aPackage.getParentPackage(); parentPackage != null; parentPackage = parentPackage.getParentPackage()) {
      final String packageName = parentPackage.getName();
      if (packageName == null || packageName.length() == 0) {
        break; // reached default package
      }
      name.insert(0, ".");
      if (packageName.length() > 2 && abbreviator.shouldAbbreviateName(parentPackage)) {
        name.insert(0, packageName.substring(0, 1));
      }
      else {
        name.insert(0, packageName);
      }
    }
    return name.toString();
  }

  /**
   * a directory is considered "empty" if it has at least one child and all its children are only directories
   * @param strictlyEmpty if true, the package is considered empty if it has only 1 child and this child  is a directory
   *        otherwise the package is considered as empty if all direct children that it has are directories
   */
  public static final boolean isEmptyMiddlePackage(PsiDirectory dir, boolean strictlyEmpty) {
    final PsiElement[] children = dir.getChildren();
    if (children.length == 0) {
      return false;
    }
    final int subpackagesCount = getSubpackagesCount(dir);
    return strictlyEmpty? children.length == 1 && (children.length == subpackagesCount) : (children.length == subpackagesCount);
  }

  private static int getSubpackagesCount(PsiDirectory dir) {
    int count = 0;
    final PsiDirectory[] subdirs = dir.getSubdirectories();
    for (int idx = 0; idx < subdirs.length; idx++) {
      final PsiDirectory subdir = subdirs[idx];
      count += subdir.getPackage() != null? 1 : 0;
    }
    return count;
  }
}
