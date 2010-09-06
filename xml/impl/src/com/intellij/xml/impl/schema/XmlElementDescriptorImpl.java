/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementDescriptorAwareAboutChildren;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl implements XmlElementDescriptor, PsiWritableMetaData, Validator<XmlTag>,
                                                 XmlElementDescriptorAwareAboutChildren {
  protected XmlTag myDescriptorTag;
  protected volatile XmlNSDescriptor NSDescriptor;
  private volatile @Nullable Validator<XmlTag> myValidator;

  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";
  @NonNls
  public static final String NONQUALIFIED_ATTR_VALUE = "unqualified";
  @NonNls
  private static final String ELEMENT_FORM_DEFAULT = "elementFormDefault";

  public XmlElementDescriptorImpl(XmlTag descriptorTag) {
    myDescriptorTag = descriptorTag;
  }

  public XmlElementDescriptorImpl() {}

  public PsiElement getDeclaration(){
    return myDescriptorTag;
  }

  public String getName(PsiElement context){
    String value = myDescriptorTag.getAttributeValue("name");

    if(context instanceof XmlElement){
      final String namespace = getNamespaceByContext(context);
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

      if(tag != null){
        final String namespacePrefix = tag.getPrefixByNamespace(namespace);

        if (namespacePrefix != null && namespacePrefix.length() > 0) {
          final XmlTag rootTag = ((XmlFile)myDescriptorTag.getContainingFile()).getDocument().getRootTag();
          String elementFormDefault;

          if (rootTag != null && 
              ( NONQUALIFIED_ATTR_VALUE.equals(elementFormDefault = rootTag.getAttributeValue(ELEMENT_FORM_DEFAULT)) || elementFormDefault == null /*unqualified is default*/) &&
              tag.getNamespaceByPrefix("").length() == 0
             ) {
            value = XmlUtil.findLocalNameByQualifiedName(value);
          } else {
            value = namespacePrefix + ":" + XmlUtil.findLocalNameByQualifiedName(value);
          }
        }
      }
    }
    return value;
  }

  /** getter for _local_ name */
  public String getName() {
    return XmlUtil.findLocalNameByQualifiedName(getName(null));
  }

  public String getNamespaceByContext(PsiElement context){
    //while(context != null){
    //  if(context instanceof XmlTag){
    //    final XmlTag contextTag = ((XmlTag)context);
    //    final XmlNSDescriptorImpl schemaDescriptor = XmlUtil.findXmlNSDescriptorByType(contextTag);
    //    if (schemaDescriptor != null) {
    //      return schemaDescriptor.getDefaultNamespace();
    //    }
    //  }
    //  context = context.getContext();
    //}
    return getNamespace();
  }

  public String getNamespace(){
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(getName(null));
    final XmlNSDescriptorImpl xmlNSDescriptor = (XmlNSDescriptorImpl)getNSDescriptor();
    if(xmlNSDescriptor == null || myDescriptorTag == null) return XmlUtil.EMPTY_URI;
    return "".equals(namespacePrefix) ?
           xmlNSDescriptor.getDefaultNamespace() :
           myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
  }

  public void init(PsiElement element){
    if (myDescriptorTag!=element && myDescriptorTag!=null) {
      NSDescriptor = null;
    }
    myDescriptorTag = (XmlTag) element;
  }

  public Object[] getDependences(){
    return new Object[]{myDescriptorTag};
  }

  private XmlNSDescriptor getNSDescriptor(XmlElement context) {
    XmlNSDescriptor nsDescriptor = getNSDescriptor();
    if (context instanceof XmlTag && nsDescriptor instanceof XmlNSDescriptorImpl) {
      final String defaultNamespace = ((XmlNSDescriptorImpl)nsDescriptor).getDefaultNamespace();
      if (XmlUtil.XML_SCHEMA_URI.equals(defaultNamespace)) return nsDescriptor; // do not check for overriden for efficiency

      final XmlTag tag = (XmlTag)context;
      final String tagNs = tag.getNamespace();
      if (tagNs.equals(defaultNamespace)) {
        XmlNSDescriptor previousDescriptor = nsDescriptor;
        nsDescriptor = tag.getNSDescriptor(tagNs, true);
        if (nsDescriptor == null) nsDescriptor = previousDescriptor;
      }
    }
    
    return nsDescriptor;
  }

  public XmlNSDescriptor getNSDescriptor() {
    XmlNSDescriptor nsDescriptor = NSDescriptor;
    if (nsDescriptor == null || !NSDescriptor.getDeclaration().isValid()) {
      final XmlFile file = XmlUtil.getContainingFile(getDeclaration());
      if(file == null) return null;
      final XmlDocument document = file.getDocument();
      if(document == null) return null;
      NSDescriptor = nsDescriptor = (XmlNSDescriptor)document.getMetaData();
    }

    return nsDescriptor;
  }

  @Override
  public Integer getMinOccurs() {
    String value = myDescriptorTag.getAttributeValue("minOccurs");
    try {
      return value == null ? 1 : Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Integer getMaxOccurs() {
    String value = myDescriptorTag.getAttributeValue("maxOccurs");
    try {
      return value == null ? 1 : "unbounded".equals(value) ? Integer.MAX_VALUE : Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public TypeDescriptor getType() {
    return getType(null);
  }

  @Nullable
  public TypeDescriptor getType(XmlElement context) {
    final XmlNSDescriptor nsDescriptor = getNSDescriptor(context);
    if (!(nsDescriptor instanceof XmlNSTypeDescriptorProvider)) return null;

    TypeDescriptor type = ((XmlNSTypeDescriptorProvider) nsDescriptor).getTypeDescriptor(myDescriptorTag);
    if (type == null) {
      String substAttr = myDescriptorTag.getAttributeValue("substitutionGroup");
      if (substAttr != null) {
        final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(substAttr);
        final String namespace = "".equals(namespacePrefix) ?
                                 ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
                                 myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
        final String local = XmlUtil.findLocalNameByQualifiedName(substAttr);
        final XmlElementDescriptorImpl originalElement = (XmlElementDescriptorImpl)((XmlNSDescriptorImpl)getNSDescriptor()).getElementDescriptor(local, namespace);
        if (originalElement != null) {
          type = originalElement.getType(context);
        }
      }
    }
    return type;
  }

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    if (context != null) {
      final XmlElementDescriptor parentDescriptorByType = XmlUtil.findXmlDescriptorByType(context);
      if (parentDescriptorByType != null && !parentDescriptorByType.equals(this)) {
        return parentDescriptorByType.getElementsDescriptors(context);
      }
    }

    XmlElementDescriptor[] elementsDescriptors = getElementsDescriptorsImpl(context);

    final TypeDescriptor type = getType(context);

    if (type instanceof ComplexTypeDescriptor) {
      final ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      String contextNs;
      PsiFile containingFile = context != null ? context.getContainingFile():null;

      if (context != null && !containingFile.isPhysical()) {
        containingFile = containingFile.getOriginalFile();
        //context = context.getParentTag();
      }

      if (context != null &&
          ( descriptor.canContainTag(context.getLocalName(), contextNs = context.getNamespace(), context ) &&
            (!contextNs.equals(getNamespace()) || descriptor.hasAnyInContentModel())
          ) ) {
        final XmlNSDescriptor nsDescriptor = getNSDescriptor();

        if (nsDescriptor != null) {
          elementsDescriptors = ArrayUtil.mergeArrays(
            elementsDescriptors,
            nsDescriptor.getRootElementsDescriptors(((XmlFile)containingFile).getDocument()),
            XmlElementDescriptor.class
          );
        }
      }
    }

    return elementsDescriptors;
  }

  private XmlElementDescriptor[] getElementsDescriptorsImpl(XmlElement context) {
    TypeDescriptor type = getType(context);

    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;

      return typeDescriptor.getElements(context);
    }

    return EMPTY_ARRAY;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    TypeDescriptor type = getType(context);

    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
      XmlAttributeDescriptor[] attributeDescriptors = typeDescriptor.getAttributes(context);

      if (context != null) {
        final String contextNs = context.getNamespace();

        boolean seenXmlNs = false;
        for(String ns:context.knownNamespaces()) {
          if (!contextNs.equals(ns) && ns.length() > 0) {
            seenXmlNs |= XmlUtil.XML_NAMESPACE_URI.equals(ns);
            attributeDescriptors = updateAttributeDescriptorsFromAny(context, typeDescriptor, attributeDescriptors, ns);
          }
        }

        if (!seenXmlNs) {
          attributeDescriptors = updateAttributeDescriptorsFromAny(context, typeDescriptor, attributeDescriptors, XmlUtil.XML_NAMESPACE_URI);
        }
      }
      return attributeDescriptors;
    }

    return XmlAttributeDescriptor.EMPTY;
  }

  private XmlAttributeDescriptor[] updateAttributeDescriptorsFromAny(final XmlTag context, final ComplexTypeDescriptor typeDescriptor,
                                                                     XmlAttributeDescriptor[] attributeDescriptors,
                                                                     final String ns) {
    if (typeDescriptor.canContainAttribute("any",ns) != ComplexTypeDescriptor.CanContainAttributeType.CanNotContain) {
      final XmlNSDescriptor descriptor = context.getNSDescriptor(ns, true);

      if (descriptor instanceof XmlNSDescriptorImpl) {
        attributeDescriptors = ArrayUtil.mergeArrays(
          attributeDescriptors,
          ((XmlNSDescriptorImpl)descriptor).getRootAttributeDescriptors(context),
          XmlAttributeDescriptor.class
        );
      }
    }
    return attributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context){
    return getAttributeDescriptorImpl(attributeName,context);
  }

  private XmlAttributeDescriptor getAttributeDescriptorImpl(final String attributeName, XmlTag context) {
    final String localName = XmlUtil.findLocalNameByQualifiedName(attributeName);
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(attributeName);
    final XmlNSDescriptorImpl xmlNSDescriptor = (XmlNSDescriptorImpl)getNSDescriptor();
    final String namespace = "".equals(namespacePrefix) ?
                             ((xmlNSDescriptor != null)?xmlNSDescriptor.getDefaultNamespace():"") :
                             context.getNamespaceByPrefix(namespacePrefix);

    XmlAttributeDescriptor attribute = getAttribute(localName, namespace, context, attributeName);
    
    if (attribute instanceof AnyXmlAttributeDescriptor && namespace.length() > 0) {
      final XmlNSDescriptor candidateNSDescriptor = context.getNSDescriptor(namespace, true);

      if (candidateNSDescriptor instanceof XmlNSDescriptorImpl) {
        final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)candidateNSDescriptor;

        final XmlAttributeDescriptor xmlAttributeDescriptor = nsDescriptor.getAttribute(localName, namespace, context);
        if (xmlAttributeDescriptor != null) return xmlAttributeDescriptor;
        else {
          final ComplexTypeDescriptor.CanContainAttributeType containAttributeType =
            ((AnyXmlAttributeDescriptor)attribute).getCanContainAttributeType();
          if (containAttributeType == ComplexTypeDescriptor.CanContainAttributeType.CanContainButDoNotSkip) {
            attribute = null;
          }
        }
      }
    }
    return attribute;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute){
    return getAttributeDescriptorImpl(attribute.getName(),attribute.getParent());
  }

  private XmlAttributeDescriptor getAttribute(String attributeName, String namespace, XmlTag context, String qName) {
    XmlAttributeDescriptor[] descriptors = getAttributesDescriptors(context);

    for (XmlAttributeDescriptor descriptor : descriptors) {
      if (descriptor.getName().equals(attributeName) &&
          descriptor.getName(context).equals(qName)
         ) {
        return descriptor;
      }
    }

    TypeDescriptor type = getType(context);
    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      final ComplexTypeDescriptor.CanContainAttributeType containAttributeType = descriptor.canContainAttribute(attributeName, namespace);

      if (containAttributeType != ComplexTypeDescriptor.CanContainAttributeType.CanNotContain) {
        return new AnyXmlAttributeDescriptor(attributeName, containAttributeType);
      }
    }

    return null;
  }

  public int getContentType() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      final XmlElementDescriptor[] elements = ((ComplexTypeDescriptor)type).getElements(null);

      if (elements.length > 0) return CONTENT_TYPE_CHILDREN;
      return CONTENT_TYPE_EMPTY;
    }

    return CONTENT_TYPE_MIXED;
  }

  public XmlElementDescriptor getElementDescriptor(final String name) {
      final String localName = XmlUtil.findLocalNameByQualifiedName(name);
      final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);
      final String namespace = "".equals(namespacePrefix) ?
                               ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
                               myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
    return getElementDescriptor(localName, namespace, null, name);
  }

  protected XmlElementDescriptor getElementDescriptor(final String localName, final String namespace, XmlElement context, String fullName) {
    XmlElementDescriptor[] elements = getElementsDescriptorsImpl(context);

    for (XmlElementDescriptor element1 : elements) {
      final XmlElementDescriptorImpl element = (XmlElementDescriptorImpl)element1;
      final String namespaceByContext = element.getNamespaceByContext(context);

      if (element.getName().equals(localName)) {
        if ( namespace == null ||
             namespace.equals(namespaceByContext) ||
             namespaceByContext.equals(XmlUtil.EMPTY_URI) ||
             element.getName(context).equals(fullName)
           ) {
          return element;
        } else if ((namespace == null || namespace.length() == 0) &&
                   element.getDefaultName().equals(fullName)) {
          return element;
        } else {
          final XmlNSDescriptor descriptor = context instanceof XmlTag? ((XmlTag)context).getNSDescriptor(namespace, true) : null;

          // schema's targetNamespace could be different from file systemId used as NS
          if (descriptor instanceof XmlNSDescriptorImpl &&
              ((XmlNSDescriptorImpl)descriptor).getDefaultNamespace().equals(namespaceByContext)
             ) {
            return element;
          }
        }
      }
    }

    TypeDescriptor type = getType(context);
    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      if (descriptor.canContainTag(localName, namespace, context)) {
        return new AnyXmlElementDescriptor(this, getNSDescriptor());
      }
    }

    return null;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag element, XmlTag contextTag){
    final XmlElement context = (XmlElement)element.getParent();

    XmlElementDescriptor elementDescriptor = getElementDescriptor(
      element.getLocalName(),
      element.getNamespace(), context,
      element.getName()
    );

    if(elementDescriptor == null || element.getAttributeValue("xsi:type") != null){
      final XmlElementDescriptor xmlDescriptorByType = XmlUtil.findXmlDescriptorByType(element);

      if (xmlDescriptorByType != null) elementDescriptor = xmlDescriptorByType;
      else if (context instanceof XmlTag && ((XmlTag)context).getAttributeValue("xsi:type") != null && askParentDescriptorViaXsi()) {
        final XmlElementDescriptor parentXmlDescriptorByType = XmlUtil.findXmlDescriptorByType(((XmlTag)context));
        if (parentXmlDescriptorByType != null) {
          elementDescriptor = parentXmlDescriptorByType.getElementDescriptor(element, contextTag);
        }
      }
    }
    return elementDescriptor;
  }

  protected boolean askParentDescriptorViaXsi() {
    return true;
  }

  public String getQualifiedName() {
    if (!"".equals(getNS())) {
      return getNS() + ":" + getName();
    }

    return getName();
  }

  private String getNS(){
    return XmlUtil.findNamespacePrefixByURI((XmlFile) myDescriptorTag.getContainingFile(), getNamespace());
  }

  public String getDefaultName() {
    final PsiFile psiFile = myDescriptorTag.getContainingFile();
    XmlTag rootTag = psiFile instanceof XmlFile ?((XmlFile)psiFile).getDocument().getRootTag():null;

    if (rootTag != null && QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue(ELEMENT_FORM_DEFAULT))) {
      return getQualifiedName();
    }

    return getName();
  }

  public boolean isAbstract() {
    return isAbstractDeclaration(myDescriptorTag);
  }

  public static Boolean isAbstractDeclaration(final XmlTag descriptorTag) {
    return Boolean.valueOf(descriptorTag.getAttributeValue("abstract"));
  }

  public void setName(String name) throws IncorrectOperationException {
    NamedObjectDescriptor.setName(myDescriptorTag, name);
  }

  public void setValidator(final Validator<XmlTag> validator) {
    myValidator = validator;
  }

  public void validate(@NotNull XmlTag context, @NotNull ValidationHost host) {
    Validator<XmlTag> validator = myValidator;
    if (validator != null) {
      validator.validate(context, host);
    }
  }

  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    final TypeDescriptor type = getType(context);
    
    if (type instanceof ComplexTypeDescriptor) {
      final ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
      return typeDescriptor.canContainTag("a", namespace, context) ||
             typeDescriptor.getNsDescriptors().hasSubstitutions() ||
             XmlUtil.nsFromTemplateFramework(namespace)
        ;
    }
    return false;
  }

  @Override
  public String toString() {
    return getName() + " (" + getNamespace() + ")";
  }
}
