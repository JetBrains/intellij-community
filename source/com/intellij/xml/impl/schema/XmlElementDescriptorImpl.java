package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl implements XmlElementDescriptor {
  protected XmlTag myDescriptorTag;
  protected XmlNSDescriptor NSDescriptor;

  public XmlElementDescriptorImpl(XmlTag descriptorTag) {
    myDescriptorTag = descriptorTag;
  }

  public XmlElementDescriptorImpl() {}

  public PsiElement getDeclaration(){
    return myDescriptorTag;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    return true;
  }

  public String getName(PsiElement context){
    String value = myDescriptorTag.getAttributeValue("name");
    if(context instanceof XmlElement){
      final String namespace = getNamespaceByContext(context);
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
      if(tag != null){
        final String namespacePrefix = tag.getPrefixByNamespace(namespace);
        if(namespacePrefix != null && namespacePrefix.length() > 0)
          value = namespacePrefix + ":" + XmlUtil.findLocalNameByQualifiedName(value);
      }
    }
    return value;
  }

  /** getter for _local_ name */
  public String getName() {
    return XmlUtil.findLocalNameByQualifiedName(getName(null));
  }

  public String getNamespaceByContext(PsiElement context){
    while(context != null){
      if(context instanceof XmlTag){
        final XmlTag contextTag = ((XmlTag)context);
        final String typeAttr = contextTag.getAttributeValue("type", XmlUtil.XML_SCHEMA_INSTANCE_URI);
        if(typeAttr != null) return contextTag.getNamespace();
      }
      context = context.getContext();
    }
    return getNamespace();
  }

  public String getNamespace(){
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(getName(null));
    return "".equals(namespacePrefix) ?
      ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
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

  public XmlNSDescriptor getNSDescriptor() {
    if (NSDescriptor==null) {
      final XmlFile file = (XmlFile) XmlUtil.getContainingFile(getDeclaration());
      if(file == null) return null;
      final XmlDocument document = file.getDocument();
      if(document == null) return null;
      NSDescriptor = (XmlNSDescriptor)document.getMetaData();
    }

    return NSDescriptor;
  }

  public TypeDescriptor getType() {
    final XmlNSDescriptor nsDescriptor = getNSDescriptor();
    if (nsDescriptor == null) return null;

    TypeDescriptor type = ((XmlNSDescriptorImpl) nsDescriptor).getTypeDescriptor(myDescriptorTag);
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
          type = originalElement.getType();
        }
      }
    }
    return type;
  }

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return getElementsDescriptors();
  }

  private XmlElementDescriptor[] getElementsDescriptors() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;

      return typeDescriptor.getElements();
    }

    return EMPTY_ARRAY;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
      return typeDescriptor.getAttributes();
    }

    return new XmlAttributeDescriptor[0];
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName){
    final String localName = XmlUtil.findLocalNameByQualifiedName(attributeName);
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(attributeName);
    final String namespace = "".equals(namespacePrefix) ?
      ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
      myDescriptorTag.getNamespaceByPrefix(namespacePrefix);

    return getAttribute(localName, namespace);
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute){
    return getAttributeDescriptor(attribute.getName());
  }

  public XmlAttributeDescriptor getAttribute(String attributeName, String namespace) {
    XmlAttributeDescriptor[] descriptors = getAttributesDescriptors();

    for (int i = 0; i < descriptors.length; i++) {
      XmlAttributeDescriptor descriptor = descriptors[i];

      if (descriptor.getName().equals(attributeName)) {
        return descriptor;
      }
    }

    TypeDescriptor type = getType();
    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      if (descriptor.canContainAttribute(attributeName, namespace)) {
        return new AnyXmlAttributeDescriptor(attributeName);
      }
    }

    return null;
  }

  public int getContentType() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      return CONTENT_TYPE_CHILDREN;
    }

    return CONTENT_TYPE_MIXED;
  }

  public XmlElementDescriptor getElementDescriptor(final String name) {
      final String localName = XmlUtil.findLocalNameByQualifiedName(name);
      final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);
      final String namespace = "".equals(namespacePrefix) ?
        ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
        myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
    return getElementDescriptor(localName, namespace, null);
  }

  protected XmlElementDescriptor getElementDescriptor(final String localName, final String namespace, XmlElement context) {
    XmlElementDescriptor[] elements = getElementsDescriptors();

    for (int i = 0; i < elements.length; i++) {
      XmlElementDescriptorImpl element = (XmlElementDescriptorImpl) elements[i];
      if (element.getName().equals(localName) && element.getNamespaceByContext(context).equals(namespace)) {
        return element;
      }
    }

    TypeDescriptor type = getType();
    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      if (descriptor.canContainTag(localName, namespace)) {
        return new AnyXmlElementDescriptor(this, getNSDescriptor());
      }
    }

    return null;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag element){
    XmlElementDescriptor elementDescriptor = getElementDescriptor(element.getLocalName(), element.getNamespace(), (XmlElement)element.getParent());
    if(elementDescriptor == null){
      final String type = element.getAttributeValue("type", XmlUtil.XML_SCHEMA_INSTANCE_URI);
      if(type != null){
        final String namespaceByPrefix = element.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(type));
        final XmlNSDescriptor typeDecr = element.getNSDescriptor(namespaceByPrefix, false);
        if(typeDecr instanceof XmlNSDescriptorImpl){
          final XmlNSDescriptorImpl schemaDescriptor = ((XmlNSDescriptorImpl)typeDecr);
          final XmlElementDescriptor descriptorByType = schemaDescriptor.getDescriptorByType(type, element);
          elementDescriptor = descriptorByType;
        }
      }
    }
    return elementDescriptor;
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
    XmlTag rootTag = ((XmlFile)myDescriptorTag.getContainingFile()).getDocument().getRootTag();

    if ("qualified".equals(rootTag.getAttributeValue("elementFormDefault"))) {
      return getQualifiedName();
    }

    return getName();
  }

  public boolean isAbstract() {
    return "true".equals(myDescriptorTag.getAttributeValue("abstract"));
  }

}
