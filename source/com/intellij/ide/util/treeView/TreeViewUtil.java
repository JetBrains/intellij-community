/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;

import java.util.concurrent.ConcurrentMap;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 6, 2004
 */
public class TreeViewUtil {
  private static final int SUBPACKAGE_LIMIT = 2;
  private static final Key<ResolveCache.MapPair<PsiPackage, Boolean>> SHOULD_ABBREV_PACK_KEY = Key.create("PACK_ABBREV_CACHE");

  private static boolean shouldAbbreviateName(PsiPackage aPackage) {
    ConcurrentMap<PsiPackage,Boolean> map =
      ((PsiManagerEx)PsiManager.getInstance(aPackage.getProject())).getResolveCache().getOrCreateWeakMap(SHOULD_ABBREV_PACK_KEY, true);
    Boolean ret = map.get(aPackage);
    if (ret != null) return ret;
    ret = scanPackages(aPackage, 1);
    map.put(aPackage, ret);
    return ret;
  }

  private static boolean scanPackages(PsiPackage p, int packageNameOccurrencesFound) {
    final PsiPackage[] subPackages = p.getSubPackages();
    packageNameOccurrencesFound += subPackages.length;
    if (packageNameOccurrencesFound > SUBPACKAGE_LIMIT) {
      return true;
    }
    for (PsiPackage subPackage : subPackages) {
      if (scanPackages(subPackage, packageNameOccurrencesFound)) {
        return true;
      }
    }
    return false;
  }

  public static String calcAbbreviatedPackageFQName(PsiPackage aPackage) {
    final StringBuilder name = new StringBuilder(aPackage.getName());
    for (PsiPackage parentPackage = aPackage.getParentPackage(); parentPackage != null; parentPackage = parentPackage.getParentPackage()) {
      final String packageName = parentPackage.getName();
      if (packageName == null || packageName.length() == 0) {
        break; // reached default package
      }
      name.insert(0, ".");
      if (packageName.length() > 2 && shouldAbbreviateName(parentPackage)) {
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
   *
   * @param strictlyEmpty if true, the package is considered empty if it has only 1 child and this child  is a directory
   *                      otherwise the package is considered as empty if all direct children that it has are directories
   */
  public static boolean isEmptyMiddlePackage(PsiDirectory dir, boolean strictlyEmpty) {
    final PsiElement[] children = dir.getChildren();
    if (children.length == 0) {
      return false;
    }
    final int subpackagesCount = getSubpackagesCount(dir);
    return strictlyEmpty ? children.length == 1 && children.length == subpackagesCount : children.length == subpackagesCount;
  }

  private static int getSubpackagesCount(PsiDirectory dir) {
    int count = 0;
    final PsiDirectory[] subdirs = dir.getSubdirectories();
    for (final PsiDirectory subdir : subdirs) {
      count += JavaDirectoryService.getInstance().getPackage(subdir) != null ? 1 : 0;
    }
    return count;
  }
}
