/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2002
 * Time: 10:11:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.RefPackage;

import java.util.Comparator;

public class RefEntityAlphabeticalComparator implements Comparator {
  private static RefEntityAlphabeticalComparator ourEntity;

  public int compare(Object o1, Object o2) {

    if (o1 == o2) return 0;

    if (o1 instanceof RefPackage && !(o2 instanceof RefPackage)) {
      return 1;
    }

    if (o2 instanceof RefPackage && !(o1 instanceof RefPackage)) {
      return -1;
    }

    if (o1 instanceof RefPackage) {
      return ((RefPackage)o1).getQualifiedName().compareToIgnoreCase(((RefPackage)o2).getQualifiedName());
    }

    return o1.toString().compareToIgnoreCase(o2.toString());
  }

  public static RefEntityAlphabeticalComparator getInstance() {
    if (ourEntity == null) {
      ourEntity = new RefEntityAlphabeticalComparator();
    }

    return ourEntity;
  }
}
