package com.intellij.xml.impl.schema;

import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;

import java.util.HashSet;

import org.jetbrains.annotations.NonNls;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor {
  private XmlTag myTag;
  String myUse;
  @NonNls
  public static final String REQUIRED_ATTR_VALUE = "required";
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlAttributeDescriptorImpl(XmlTag tag) {
    myTag = tag;
    myUse = myTag.getAttributeValue("use");
  }

  public XmlAttributeDescriptorImpl() {}

  public PsiElement getDeclaration(){
    return myTag;
  }

  public String getName() {
    return myTag.getAttributeValue("name");
  }

  public void init(PsiElement element){
    myTag = (XmlTag) element;
    myUse = myTag.getAttributeValue("use");
  }

  public Object[] getDependences(){
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isRequired() {
    return REQUIRED_ATTR_VALUE.equals(myUse);
  }

  public boolean isFixed() {
    return myTag.getAttributeValue("fixed") != null;
  }

  private boolean hasSimpleSchemaType(@NonNls String type) {
    final String attributeValue = myTag.getAttributeValue("type");

    if (attributeValue != null) {
      if (attributeValue.endsWith(type)) {
        final String namespacePrefix = myTag.getNamespacePrefix();

        if (namespacePrefix.length() > 0) {
          return attributeValue.equals(namespacePrefix+":"+type);
        } else {
          return attributeValue.equals(type);
        }
      }
    }

    return false;
  }

  public boolean hasIdType() {
    return hasSimpleSchemaType("ID");
  }

  public boolean hasIdRefType() {
    return hasSimpleSchemaType("IDREF");
  }

  public String getDefaultValue() {
    if (isFixed()) {
      return myTag.getAttributeValue("fixed");
    }

    return myTag.getAttributeValue("default");
  }

  //todo: refactor to hierarchy of value descriptor?
  public boolean isEnumerated() {
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(myTag);

    return elementDescriptor != null &&
           elementDescriptor.getType() instanceof ComplexTypeDescriptor &&
           getEnumeratedValues(elementDescriptor).length != 0;
  }

  public String[] getEnumeratedValues() {
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(myTag);

    if (elementDescriptor!=null && elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      return getEnumeratedValues(elementDescriptor);
    }

    final String namespacePrefix = myTag.getNamespacePrefix();
    XmlTag type = myTag.findFirstSubTag(
      ((namespacePrefix.length() > 0)?namespacePrefix+":":"")+"simpleType"
    );

    if (type != null) {
      return getEnumeratedValuesImpl(type);
    }

    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private String[] getEnumeratedValuesImpl(final XmlTag declaration) {
    if ("boolean".equals(declaration.getAttributeValue("name"))) {
      return new String[] {"true", "false"};
    }

    final HashSet<String> variants = new HashSet<String>();
    XmlUtil.collectEnumerationValues(declaration,variants);

    if (variants.size() > 0) {
      return variants.toArray(new String[variants.size()]);
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private String[] getEnumeratedValues(final XmlElementDescriptorImpl elementDescriptor) {
    return getEnumeratedValuesImpl(((ComplexTypeDescriptor)elementDescriptor.getType()).getDeclaration());
  }

  public String getName(PsiElement context){
    final XmlTag rootTag = (((XmlFile) myTag.getContainingFile())).getDocument().getRootTag();
    String attributeValue = rootTag.getAttributeValue("targetNamespace");
    XmlTag tag = (XmlTag)context;

    String name = getName();
    if (attributeValue != null && //!attributeValue.equals(tag.getNamespace())) &&
        QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("attributeFormDefault"))) {
      final String prefixByNamespace = tag.getPrefixByNamespace(attributeValue);
      if (prefixByNamespace!= null && prefixByNamespace.length() > 0) {
        name = prefixByNamespace + ":" + name;
      }
    }

    return name;
  }
}
