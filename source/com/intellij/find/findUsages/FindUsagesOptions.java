
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class FindUsagesOptions implements Cloneable {
  public SearchScope searchScope;

  public boolean isSearchForTextOccurences = true;

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
  public boolean isIncludeOverloadUsages = false;
  public boolean isThrowUsages = false;
  public ThrowSearchUtil.Root myThrowRoot = null;

  public FindUsagesOptions(@NotNull Project project) {
    searchScope = project.getProjectScope();
  }

  public Object clone() {
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FindUsagesOptions that = (FindUsagesOptions)o;

    if (isCheckDeepInheritance != that.isCheckDeepInheritance) return false;
    if (isClassesUsages != that.isClassesUsages) return false;
    if (isDerivedClasses != that.isDerivedClasses) return false;
    if (isDerivedInterfaces != that.isDerivedInterfaces) return false;
    if (isFieldsUsages != that.isFieldsUsages) return false;
    if (isImplementingClasses != that.isImplementingClasses) return false;
    if (isImplementingMethods != that.isImplementingMethods) return false;
    if (isIncludeInherited != that.isIncludeInherited) return false;
    if (isIncludeOverloadUsages != that.isIncludeOverloadUsages) return false;
    if (isIncludeSubpackages != that.isIncludeSubpackages) return false;
    if (isMethodsUsages != that.isMethodsUsages) return false;
    if (isOverridingMethods != that.isOverridingMethods) return false;
    if (isReadAccess != that.isReadAccess) return false;
    if (isSearchForTextOccurences != that.isSearchForTextOccurences) return false;
    if (isSkipImportStatements != that.isSkipImportStatements) return false;
    if (isSkipPackageStatements != that.isSkipPackageStatements) return false;
    if (isThrowUsages != that.isThrowUsages) return false;
    if (isUsages != that.isUsages) return false;
    if (isWriteAccess != that.isWriteAccess) return false;
    if (myThrowRoot != null ? !myThrowRoot.equals(that.myThrowRoot) : that.myThrowRoot != null) return false;
    if (searchScope != null ? !searchScope.equals(that.searchScope) : that.searchScope != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (searchScope != null ? searchScope.hashCode() : 0);
    result = 31 * result + (isSearchForTextOccurences ? 1 : 0);
    result = 31 * result + (isUsages ? 1 : 0);
    result = 31 * result + (isClassesUsages ? 1 : 0);
    result = 31 * result + (isMethodsUsages ? 1 : 0);
    result = 31 * result + (isFieldsUsages ? 1 : 0);
    result = 31 * result + (isDerivedClasses ? 1 : 0);
    result = 31 * result + (isImplementingClasses ? 1 : 0);
    result = 31 * result + (isDerivedInterfaces ? 1 : 0);
    result = 31 * result + (isOverridingMethods ? 1 : 0);
    result = 31 * result + (isImplementingMethods ? 1 : 0);
    result = 31 * result + (isIncludeSubpackages ? 1 : 0);
    result = 31 * result + (isSkipImportStatements ? 1 : 0);
    result = 31 * result + (isSkipPackageStatements ? 1 : 0);
    result = 31 * result + (isCheckDeepInheritance ? 1 : 0);
    result = 31 * result + (isIncludeInherited ? 1 : 0);
    result = 31 * result + (isReadAccess ? 1 : 0);
    result = 31 * result + (isWriteAccess ? 1 : 0);
    result = 31 * result + (isIncludeOverloadUsages ? 1 : 0);
    result = 31 * result + (isThrowUsages ? 1 : 0);
    result = 31 * result + (myThrowRoot != null ? myThrowRoot.hashCode() : 0);
    return result;
  }
}
