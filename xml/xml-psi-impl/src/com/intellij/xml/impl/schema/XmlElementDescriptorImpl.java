/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.*;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl extends XsdEnumerationDescriptor<XmlTag>
  implements XmlElementDescriptor, PsiWritableMetaData, Validator<XmlTag>,
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

  public XmlElementDescriptorImpl(@Nullable XmlTag descriptorTag) {
    myDescriptorTag = descriptorTag;
  }

  public XmlElementDescriptorImpl() {}

  @Override
  public XmlTag getDeclaration(){
    return myDescriptorTag;
  }

  @Override
  public String getName(PsiElement context){
    String value = myDescriptorTag.getAttributeValue("name");

    if(context instanceof XmlElement){
      final String namespace = getNamespaceByContext(context);
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

      if(tag != null){
        final String namespacePrefix = tag.getPrefixByNamespace(namespace);

        if (namespacePrefix != null && namespacePrefix.length() > 0) {
          final XmlTag rootTag = ((XmlFile)myDescriptorTag.getContainingFile()).getRootTag();
          String elementFormDefault;

          if (rootTag != null && 
              ( NONQUALIFIED_ATTR_VALUE.equals(elementFormDefault = rootTag.getAttributeValue(ELEMENT_FORM_DEFAULT)) || elementFormDefault == null /*unqualified is default*/) &&
              tag.getNamespaceByPrefix("").isEmpty()
            && myDescriptorTag.getParentTag() != rootTag
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
  @Override
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
    String name = getName();
    if (name == null) return XmlUtil.EMPTY_URI;
    if (getNSDescriptor() == null || myDescriptorTag == null) return XmlUtil.EMPTY_URI;
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);
    return namespacePrefix.isEmpty() ?
           getDefaultNamespace() :
           myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
  }

  @Override
  public void init(PsiElement element){
    if (myDescriptorTag!=element && myDescriptorTag!=null) {
      NSDescriptor = null;
    }
    myDescriptorTag = (XmlTag) element;
  }

  @Override
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

  @Override
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
  public XmlElementsGroup getTopGroup() {
    TypeDescriptor type = getType();
    return type instanceof ComplexTypeDescriptor ? ((ComplexTypeDescriptor)type).getTopGroup() : null;
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
        final String namespace = namespacePrefix.isEmpty() ?
                                 getDefaultNamespace() :
                                 myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
        final String local = XmlUtil.findLocalNameByQualifiedName(substAttr);
        final XmlElementDescriptorImpl originalElement = (XmlElementDescriptorImpl)((XmlNSDescriptorImpl)getNSDescriptor()).getElementDescriptor(local, namespace);
        if (originalElement != null && originalElement != this) {
          type = originalElement.getType(context);
        }
      }
    }
    return type;
  }

  @Override
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
      PsiFile containingFile = context != null ? context.getContainingFile():null;

      if (context != null && !containingFile.isPhysical()) {
        containingFile = containingFile.getOriginalFile();
        //context = context.getParentTag();
      }

      String contextNs;
      if (context != null &&
          descriptor.canContainTag(context.getLocalName(), contextNs = context.getNamespace(), context) &&
          (!contextNs.equals(getNamespace()) || descriptor.hasAnyInContentModel()) &&
          containingFile instanceof XmlFile) { // JSXmlLiteralExpressionImpl, being an xml element itself, may be contained in non-XML file
        final XmlNSDescriptor nsDescriptor = getNSDescriptor();

        if (nsDescriptor != null) {
          elementsDescriptors = ArrayUtil.mergeArrays(
            elementsDescriptors,
            nsDescriptor.getRootElementsDescriptors(((XmlFile)containingFile).getDocument())
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

      XmlElementDescriptor[] elements = typeDescriptor.getElements(context);
      if (context instanceof XmlTag && elements.length > 0) {
        String[] namespaces = ((XmlTag)context).knownNamespaces();
        if (namespaces.length > 1) {
          List<XmlElementDescriptor> result = new ArrayList<>(Arrays.asList(elements));
          for (String namespace : namespaces) {
            if (namespace.equals(typeDescriptor.getNsDescriptor().getDefaultNamespace())) {
              continue;
            }
            XmlNSDescriptor descriptor = ((XmlTag)context).getNSDescriptor(namespace, false);
            if (descriptor instanceof XmlNSDescriptorImpl && ((XmlNSDescriptorImpl)descriptor).hasSubstitutions()) {
              for (XmlElementDescriptor element : elements) {
                String name = XmlUtil.getLocalName(element.getName(context)).toString();
                String s = ((XmlNSDescriptorImpl)element.getNSDescriptor()).getDefaultNamespace();
                XmlElementDescriptor[] substitutes = ((XmlNSDescriptorImpl)descriptor).getSubstitutes(name, s);
                result.addAll(Arrays.asList(substitutes));
              }
            }
          }
          return result.toArray(new XmlElementDescriptor[result.size()]);
        }
      }
      return elements;
    }

    return EMPTY_ARRAY;
  }

  @Override
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

  /** <xsd:anyAttribute> directive processed here */
  private static XmlAttributeDescriptor[] updateAttributeDescriptorsFromAny(final XmlTag context,
                                                                            final ComplexTypeDescriptor typeDescriptor,
                                                                            XmlAttributeDescriptor[] attributeDescriptors,
                                                                            final String ns) {
    if (typeDescriptor.canContainAttribute(ns, null) != ComplexTypeDescriptor.CanContainAttributeType.CanNotContain) {
      // anyAttribute found
      final XmlNSDescriptor descriptor = context.getNSDescriptor(ns, true);

      if (descriptor instanceof XmlNSDescriptorImpl) {
        XmlAttributeDescriptor[] rootDescriptors = ((XmlNSDescriptorImpl)descriptor).getRootAttributeDescriptors(context);
        attributeDescriptors = ArrayUtil.mergeArrays(attributeDescriptors, rootDescriptors);
      }
    }
    return attributeDescriptors;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context){
    return getAttributeDescriptorImpl(attributeName,context);
  }

  @Nullable
  private XmlAttributeDescriptor getAttributeDescriptorImpl(final String attributeName, XmlTag context) {
    final String localName = XmlUtil.findLocalNameByQualifiedName(attributeName);
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(attributeName);
    final String namespace = namespacePrefix.isEmpty() ?
                             getDefaultNamespace() :
                             context.getNamespaceByPrefix(namespacePrefix);

    XmlAttributeDescriptor attribute = getAttribute(localName, namespace, context, attributeName);
    
    if (attribute instanceof AnyXmlAttributeDescriptor) {
      final ComplexTypeDescriptor.CanContainAttributeType containAttributeType =
        ((AnyXmlAttributeDescriptor)attribute).getCanContainAttributeType();
      if (containAttributeType != ComplexTypeDescriptor.CanContainAttributeType.CanContainAny && !namespace.isEmpty()) {
        final XmlNSDescriptor candidateNSDescriptor = context.getNSDescriptor(namespace, true);

        if (candidateNSDescriptor instanceof XmlNSDescriptorImpl) {
          final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)candidateNSDescriptor;

          final XmlAttributeDescriptor xmlAttributeDescriptor = nsDescriptor.getAttribute(localName, namespace, context);
          if (xmlAttributeDescriptor != null) return xmlAttributeDescriptor;
          else {
            if (containAttributeType == ComplexTypeDescriptor.CanContainAttributeType.CanContainButDoNotSkip) {
              attribute = null;
            }
          }
        }
      }
    }
    return attribute;
  }

  private String getDefaultNamespace() {
    XmlNSDescriptor nsDescriptor = getNSDescriptor();
    return nsDescriptor instanceof XmlNSDescriptorImpl ? ((XmlNSDescriptorImpl)nsDescriptor).getDefaultNamespace() : "";
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute){
    return getAttributeDescriptorImpl(attribute.getName(),attribute.getParent());
  }

  @Nullable
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
      final ComplexTypeDescriptor.CanContainAttributeType containAttributeType = descriptor.canContainAttribute(namespace, qName);

      if (containAttributeType != ComplexTypeDescriptor.CanContainAttributeType.CanNotContain) {
        return new AnyXmlAttributeDescriptor(attributeName, containAttributeType);
      }
    }

    return null;
  }

  @Override
  public int getContentType() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      return ((ComplexTypeDescriptor)type).getContentType();
    }

    return CONTENT_TYPE_MIXED;
  }

  @Nullable
  public XmlElementDescriptor getElementDescriptor(final String name) {
      final String localName = XmlUtil.findLocalNameByQualifiedName(name);
      final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);
      final String namespace = namespacePrefix.isEmpty() ?
                               getDefaultNamespace() :
                               myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
    return getElementDescriptor(localName, namespace, null, name);
  }

  @Nullable
  protected XmlElementDescriptor getElementDescriptor(final String localName, final String namespace, XmlElement context, String fullName) {
    XmlElementDescriptor[] elements = getElementsDescriptorsImpl(context);

    for (XmlElementDescriptor element1 : elements) {
      final XmlElementDescriptorImpl element = (XmlElementDescriptorImpl)element1;
      final String namespaceByContext = element.getNamespaceByContext(context);

      if (element.getName().equals(localName)) {
        if (namespace == null ||
            namespace.equals(namespaceByContext) ||
            namespaceByContext.equals(XmlUtil.EMPTY_URI) ||
            element.getName(context).equals(fullName) || (namespace.length() == 0) &&
                                                         element.getDefaultName().equals(fullName)
           ) {
          return element;
        }
        else {
          final XmlNSDescriptor descriptor = context instanceof XmlTag? ((XmlTag)context).getNSDescriptor(namespace, true) : null;

          // schema's targetNamespace could be different from file systemId used as NS
          if (descriptor instanceof XmlNSDescriptorImpl) {
            if (((XmlNSDescriptorImpl)descriptor).getDefaultNamespace().equals(namespaceByContext)) {
              return element;
            }
            else {
              ((XmlNSDescriptorImpl)descriptor).getSubstitutes(localName, namespace);
            }
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

  @Override
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

  @Override
  public String getQualifiedName() {
    String ns = getNS();
    if (ns != null && !ns.isEmpty()) {
      return ns + ":" + getName();
    }

    return getName();
  }

  @Nullable
  private String getNS(){
    return XmlUtil.findNamespacePrefixByURI((XmlFile) myDescriptorTag.getContainingFile(), getNamespace());
  }

  @Override
  public String getDefaultName() {
    final PsiFile psiFile = myDescriptorTag.getContainingFile();
    XmlTag rootTag = psiFile instanceof XmlFile ?((XmlFile)psiFile).getRootTag():null;

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

  @Override
  public void setName(String name) throws IncorrectOperationException {
    NamedObjectDescriptor.setName(myDescriptorTag, name);
  }

  public void setValidator(final Validator<XmlTag> validator) {
    myValidator = validator;
  }

  @Override
  public void validate(@NotNull XmlTag context, @NotNull ValidationHost host) {
    Validator<XmlTag> validator = myValidator;
    if (validator != null) {
      validator.validate(context, host);
    }
  }

  @Override
  public PsiReference[] getValueReferences(XmlTag xmlTag, @NotNull String text) {
    XmlTagValue value = xmlTag.getValue();
    XmlText[] elements = value.getTextElements();
    if (elements.length == 0 || xmlTag.getSubTags().length > 0) return PsiReference.EMPTY_ARRAY;
    return new PsiReference[] {
      new XmlEnumeratedValueReference(xmlTag, this, ElementManipulators.getValueTextRange(xmlTag))
    };
  }

  @Override
  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    final TypeDescriptor type = getType(context);
    
    if (type instanceof ComplexTypeDescriptor) {
      final ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
      return typeDescriptor.canContainTag("a", namespace, context) ||
             typeDescriptor.getNsDescriptor().hasSubstitutions() ||
             XmlUtil.nsFromTemplateFramework(namespace)
        ;
    }
    return false;
  }

  @Override
  public String toString() {
    String namespace;
    try {
      namespace = getNamespace();
    }
    catch (PsiInvalidElementAccessException e) {
      namespace = "!!!Invalid!!!";
    }
    return getName() + " (" + namespace + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    XmlElementDescriptorImpl that = (XmlElementDescriptorImpl)o;

    if (myDescriptorTag != null ? !myDescriptorTag.equals(that.myDescriptorTag) : that.myDescriptorTag != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDescriptorTag != null ? myDescriptorTag.hashCode() : 0;
  }
}
