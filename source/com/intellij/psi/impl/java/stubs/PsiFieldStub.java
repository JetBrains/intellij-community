/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;

public interface PsiFieldStub extends NamedStub<PsiField> {
  TypeInfo getType();
  String getInitializerText() throws InitializerTooLongException;
  boolean isEnumConstant ();
}