package com.intellij.psi.impl.source.html.dtd;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;

/**
 * @author Maxim.Mossienko
 */
public class HtmlAttributeDescriptorImpl implements XmlAttributeDescriptor {
  private XmlAttributeDescriptor delegate;
  private boolean myCaseSensitive;

  public HtmlAttributeDescriptorImpl(XmlAttributeDescriptor _delegate, boolean caseSensitive) {
    delegate = _delegate;
    myCaseSensitive = caseSensitive;
  }

  public boolean isRequired() {
    return delegate.isRequired();
  }

  public boolean isFixed() {
    return delegate.isFixed();
  }

  public boolean hasIdType() {
    return delegate.hasIdType();
  }

  public boolean hasIdRefType() {
    return delegate.hasIdRefType();
  }

  public String getDefaultValue() {
    return delegate.getDefaultValue();
  }

  //todo: refactor to hierarchy of value descriptor?
  public boolean isEnumerated() {
    return delegate.isEnumerated();
  }

  public String[] getEnumeratedValues() {
    return delegate.getEnumeratedValues();
  }

  public String validateValue(XmlElement context, String value) {
    if (!myCaseSensitive) value = value.toLowerCase();
    return delegate.validateValue(context, value);
  }

  public PsiElement getDeclaration() {
    return delegate.getDeclaration();
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return delegate.processDeclarations(context, processor, substitutor, lastElement, place);
  }

  public String getName(PsiElement context) {
    return delegate.getName(context);
  }

  public String getName() {
    return delegate.getName();
  }

  public void init(PsiElement element) {
    delegate.init(element);
  }

  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
