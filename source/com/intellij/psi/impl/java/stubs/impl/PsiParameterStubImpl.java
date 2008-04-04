/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiParameterStubImpl extends StubBase<PsiParameter> implements PsiParameterStub {
  private final String myName;
  private final TypeInfo myType;
  private final boolean myIsEllipsis;

  public PsiParameterStubImpl(final StubElement parent, final String name, final TypeInfo type, final boolean isEllipsis) {
    super(parent, JavaStubElementTypes.PARAMETER);
    myName = name;
    myType = type;
    myIsEllipsis = isEllipsis;
  }

  public boolean isParameterTypeEllipsis() {
    return myIsEllipsis;
  }

  public TypeInfo getParameterType() {
    return myType;
  }

  public String getName() {
    return myName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.
        append("PsiParameterListStub[").
        append(myName).append(':').append(RecordUtil.createTypeText(getParameterType())).
        append(']');
    return builder.toString();
  }
}