// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DomAttributeXmlDescriptor implements NamespaceAwareXmlAttributeDescriptor {
  private final DomAttributeChildDescription<?> myDescription;
  private final Project myProject;

  public DomAttributeXmlDescriptor(final DomAttributeChildDescription<?> description, Project project) {
    myDescription = description;
    myProject = project;
  }

  @Override
  public boolean isRequired() {
    final Required required = myDescription.getAnnotation(Required.class);
    return required != null && required.value();
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasIdType() {
    return false;
  }

  @Override
  public boolean hasIdRefType() {
    return false;
  }

  @Override
  @Nullable
  public String getDefaultValue() {
    return null;
  }//todo: refactor to hierarchy of value descriptor?

  @Override
  public boolean isEnumerated() {
    return false;
  }

  @Override
  public String @Nullable [] getEnumeratedValues() {
    return null;
  }

  @Override
  @Nullable
  public String validateValue(final XmlElement context, final String value) {
    return null;
  }

  @Override
  @Nullable
  public PsiElement getDeclaration() {
    return myDescription.getDeclaration(myProject);
  }

  @Override
  @NonNls
  public String getName(final PsiElement context) {
    return getQualifiedAttributeName(context, myDescription.getXmlName());
  }

  static String getQualifiedAttributeName(PsiElement context, XmlName xmlName) {
    final String localName = xmlName.getLocalName();
    if (context instanceof XmlTag tag) {
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

  @Override
  @NonNls
  public String getName() {
    return getLocalName();
  }

  private String getLocalName() {
    return myDescription.getXmlName().getLocalName();
  }

  @Override
  @Nullable
  public String getNamespace(@NotNull XmlTag context) {
    final DomInvocationHandler handler = DomManagerImpl.getDomManager(myProject).getDomHandler(context);

    if (handler == null) {
      return null;
    }
    return handler.createEvaluatedXmlName(myDescription.getXmlName()).getNamespace(context, handler.getFile());
  }

  @Override
  public void init(final PsiElement element) {
    throw new UnsupportedOperationException("Method init not implemented in " + getClass());
  }

  @Override
  public Object @NotNull [] getDependencies() {
    throw new UnsupportedOperationException("Method getDependencies not implemented in " + getClass());
  }
}
