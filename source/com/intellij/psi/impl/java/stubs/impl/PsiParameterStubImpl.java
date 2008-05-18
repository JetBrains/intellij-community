/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;

public class PsiParameterStubImpl extends StubBase<PsiParameter> implements PsiParameterStub {
  private final StringRef myName;
  private final TypeInfo myType;
  private final boolean myIsEllipsis;

  public PsiParameterStubImpl(final StubElement parent, final String name, final TypeInfo type, final boolean isEllipsis) {
    this(parent, StringRef.fromString(name), type, isEllipsis);
  }

  public PsiParameterStubImpl(final StubElement parent, final StringRef name, final TypeInfo type, final boolean isEllipsis) {
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

  public PsiModifierListStub getModList() {
    for (StubElement child : getChildrenStubs()) {
      if (child instanceof PsiModifierListStub) {
        return (PsiModifierListStub)child;
      }
    }
    return null;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.
        append("PsiParameterStub[").
        append(myName).append(':').append(RecordUtil.createTypeText(getParameterType())).
        append(']');
    return builder.toString();
  }
}