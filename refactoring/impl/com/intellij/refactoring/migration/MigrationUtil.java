
package com.intellij.refactoring.migration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

/**
 *
 */
public class MigrationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationUtil");

  public static UsageInfo[] findPackageUsages(PsiManager manager,
                                              PsiMigration migration,
                                              String qName) {
    PsiPackage aPackage = findOrCreatePackage(manager, migration, qName);

    return findRefs(manager, aPackage);
  }

  public static void doPackageMigration(PsiManager manager,
                                        PsiMigration migration, String newQName,
                                        UsageInfo[] usages) {
    try{
      PsiPackage aPackage = findOrCreatePackage(manager, migration, newQName);

      // rename all references
      for (UsageInfo usage : usages) {
        PsiElement element = usage.getElement();
        if (element == null || !element.isValid()) continue;
        if (element instanceof PsiJavaCodeReferenceElement) {
          ((PsiJavaCodeReferenceElement)element).bindToElement(aPackage);
        }
      }
    }
    catch(IncorrectOperationException e){
      // should not happen!
      LOG.error(e);
    }
  }

  public static UsageInfo[] findClassUsages(PsiManager manager,
                                            PsiMigration migration,
                                            String qName) {
    PsiClass aClass = findOrCreateClass(manager, migration, qName);

    return findRefs(manager, aClass);
  }

  private static UsageInfo[] findRefs(final PsiManager manager, final PsiElement aClass) {
    final ArrayList<UsageInfo> results = new ArrayList<UsageInfo>();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    PsiReference[] usages = manager.getSearchHelper().findReferences(aClass, projectScope, false);
    for (PsiReference usage : usages) {
      results.add(new UsageInfo(usage.getElement()));
    }

    return results.toArray(new UsageInfo[results.size()]);
  }

  public static void doClassMigration(PsiManager manager,
                                      PsiMigration migration,
                                      String newQName,
                                      UsageInfo[] usages) {
    try{
      PsiClass aClass = findOrCreateClass(manager, migration, newQName);

      // rename all references
      for (UsageInfo usage : usages) {
        PsiElement element = usage.getElement();
        if (element == null || !element.isValid()) continue;
        if (element instanceof PsiJavaCodeReferenceElement) {
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
          referenceElement.bindToElement(aClass);
        }
      }
    }
    catch(IncorrectOperationException e){
      // should not happen!
      LOG.error(e);
    }
  }

  static PsiPackage findOrCreatePackage(PsiManager manager, PsiMigration migration, String qName) {
    PsiPackage aPackage = manager.findPackage(qName);
    if (aPackage != null){
      return aPackage;
    }
    else{
      return migration.createPackage(qName);
    }
  }

  static PsiClass findOrCreateClass(PsiManager manager, PsiMigration migration, String qName) {
    PsiClass aClass = manager.findClass(qName);
    if (aClass == null){
      aClass = migration.createClass(qName);
    }
    return aClass;
  }
}
