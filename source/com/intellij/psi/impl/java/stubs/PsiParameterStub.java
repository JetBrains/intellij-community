/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;

public interface PsiParameterStub extends NamedStub<PsiParameter> {
  boolean isParameterTypeEllipsis();
  TypeInfo getParameterType();
}