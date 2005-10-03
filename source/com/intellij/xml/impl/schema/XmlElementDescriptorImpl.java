package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl implements XmlElementDescriptor, PsiWritableMetaData {
  protected XmlTag myDescriptorTag;
  protected XmlNSDescriptor NSDescriptor;
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

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
        if (namespacePrefix != null && namespacePrefix.length() > 0) {
          value = namespacePrefix + ":" + XmlUtil.findLocalNameByQualifiedName(value);
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

  public XmlNSDescriptor getNSDescriptor() {
    if (NSDescriptor==null) {
      final XmlFile file = XmlUtil.getContainingFile(getDeclaration());
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
    final XmlNSDescriptorImpl xmlNSDescriptor = (XmlNSDescriptorImpl)getNSDescriptor();
    final String namespace = "".equals(namespacePrefix) ?
                             ((xmlNSDescriptor != null)?xmlNSDescriptor.getDefaultNamespace():"") :
                             myDescriptorTag.getNamespaceByPrefix(namespacePrefix);

    return getAttribute(localName, namespace);
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute){
    return getAttributeDescriptor(attribute.getName());
  }

  public XmlAttributeDescriptor getAttribute(String attributeName, String namespace) {
    XmlAttributeDescriptor[] descriptors = getAttributesDescriptors();

    for (XmlAttributeDescriptor descriptor : descriptors) {
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
      final XmlElementDescriptor[] elements = ((ComplexTypeDescriptor)type).getElements();

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
    return getElementDescriptor(localName, namespace, null);
  }

  protected XmlElementDescriptor getElementDescriptor(final String localName, final String namespace, XmlElement context) {
    XmlElementDescriptor[] elements = getElementsDescriptors();

    for (XmlElementDescriptor element1 : elements) {
      final XmlElementDescriptorImpl element = (XmlElementDescriptorImpl)element1;

      final String namespaceByContext = element.getNamespaceByContext(context);
      if (element.getName().equals(localName) &&
          (namespace == null ||
           namespace.equals(namespaceByContext) ||
           namespaceByContext.equals(XmlUtil.EMPTY_URI)
          )
        ) {
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

    if(elementDescriptor == null || element.getAttributeValue("xsi:type") != null){
      final XmlElementDescriptor xmlDescriptorByType = XmlUtil.findXmlDescriptorByType(element);
      if (xmlDescriptorByType != null) elementDescriptor = xmlDescriptorByType;
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

    if (rootTag != null && QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("elementFormDefault"))) {
      return getQualifiedName();
    }

    return getName();
  }

  public boolean isAbstract() {
    return Boolean.valueOf(myDescriptorTag.getAttributeValue("abstract")).booleanValue();
  }

  public void setName(String name) throws IncorrectOperationException {
    NamedObjectDescriptor.setName(myDescriptorTag, name);
  }
}
