
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.psi.impl.search.ThrowSearchUtil;

/**
 *
 */
public class FindUsagesOptions implements Cloneable {
  public SearchScope searchScope;

  public boolean isSearchInNonJavaFiles = true;

  public boolean isUsages = false;
  public boolean isClassesUsages = false;
  public boolean isMethodsUsages = false;
  public boolean isFieldsUsages = false;
  public boolean isDerivedClasses = false;
  public boolean isImplementingClasses = false;
  public boolean isDerivedInterfaces = false;
  public boolean isOverridingMethods = false;
  public boolean isImplementingMethods = false;
  public boolean isIncludeSubpackages = true;
  public boolean isSkipImportStatements = false;
  public boolean isSkipPackageStatements = false;
  public boolean isCheckDeepInheritance = true;
  public boolean isIncludeInherited = false;
  public boolean isReadAccess = false;
  public boolean isWriteAccess = false;
  public boolean isImplementingPointcuts = false;
  public boolean isOverridingPointcuts = false;
  public boolean isIncludeOverloadUsages = false;
  public boolean isThrowUsages = false;
  public boolean isStrictThrowUsages = false;
  public ThrowSearchUtil.Root myThrowRoot = null;

  public FindUsagesOptions(final Project project, SearchScopeCache searchScopeCache) {
    searchScope = searchScopeCache.getProjectScope();
  }

  public Object clone() {
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }

  public void clear() {
    isSearchInNonJavaFiles = false;
    isUsages = false;
    isClassesUsages = false;
    isMethodsUsages = false;
    isFieldsUsages = false;
    isDerivedClasses = false;
    isImplementingClasses = false;
    isDerivedInterfaces = false;
    isOverridingMethods = false;
    isImplementingMethods = false;
    isIncludeSubpackages = false;
    isSkipImportStatements = false;
    isSkipPackageStatements = false;
    isCheckDeepInheritance = false;
    isIncludeInherited = false;
    isReadAccess = false;
    isWriteAccess = false;
    isImplementingPointcuts = false;
    isOverridingPointcuts = false;
    isIncludeOverloadUsages = false;
    isThrowUsages = false;
    isStrictThrowUsages = false;
    myThrowRoot = null;
  }
}
