package com.intellij.xml.impl.dom;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.XmlName;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class DomAttributeXmlDescriptor implements XmlAttributeDescriptor {
  private final DomAttributeChildDescription myDescription;

  public DomAttributeXmlDescriptor(final DomAttributeChildDescription description) {
    myDescription = description;
  }

  public boolean isRequired() {
    return false;
  }

  public boolean isFixed() {
    return false;
  }

  public boolean hasIdType() {
    return false;
  }

  public boolean hasIdRefType() {
    return false;
  }

  @Nullable
  public String getDefaultValue() {
    return null;
  }//todo: refactor to hierarchy of value descriptor?

  public boolean isEnumerated() {
    return false;
  }

  @Nullable
  public String[] getEnumeratedValues() {
    return null;
  }

  @Nullable
  public String validateValue(final XmlElement context, final String value) {
    return null;
  }

  public PsiElement getDeclaration() {
    return null;
  }

  @NonNls
  public String getName(final PsiElement context) {
    return getQualifiedAttributeName(context, myDescription.getXmlName());
  }

  static String getQualifiedAttributeName(PsiElement context, XmlName xmlName) {
    final String localName = xmlName.getLocalName();
    if (context instanceof XmlTag) {
      final XmlTag tag = (XmlTag)context;
      final DomInvocationHandler handler = DomManagerImpl.getDomManager(context.getProject()).getDomHandler(tag);
      if (handler != null) {
        final String ns = handler.createEvaluatedXmlName(xmlName).getNamespace(tag, handler.getFile());
        if (!ns.equals(XmlUtil.EMPTY_URI) && !ns.equals(tag.getNamespace())) {
          final String prefix = tag.getPrefixByNamespace(ns);
          if (StringUtil.isNotEmpty(prefix)) {
            return prefix + ":" + localName;
          }
        }
      }
    }

    return localName;
  }

  @NonNls
  public String getName() {
    return getLocalName();
  }

  private String getLocalName() {
    return myDescription.getXmlName().getLocalName();
  }

  public void init(final PsiElement element) {
    throw new UnsupportedOperationException("Method init not implemented in " + getClass());
  }

  public Object[] getDependences() {
    throw new UnsupportedOperationException("Method getDependences not implemented in " + getClass());
  }
}
