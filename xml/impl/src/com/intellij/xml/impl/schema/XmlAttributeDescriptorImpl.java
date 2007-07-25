package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor implements PsiWritableMetaData {
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

    if (elementDescriptor != null &&
        elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      final EnumerationData data = getEnumeratedValuesImpl(((ComplexTypeDescriptor)elementDescriptor.getType()).getDeclaration());
      return data != null && data.exaustive;
    }

    return false;
  }

  public String[] getEnumeratedValues() {
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(myTag);

    if (elementDescriptor!=null && elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      final EnumerationData data = getEnumeratedValuesImpl(((ComplexTypeDescriptor)elementDescriptor.getType()).getDeclaration());
      final String s = getDefaultValue();

      if (s != null && s.length() > 0 && data == null) {
        return new String[] {s};
      }
      return data != null? data.enumeratedValues:ArrayUtil.EMPTY_STRING_ARRAY;
    }

    final String namespacePrefix = myTag.getNamespacePrefix();
    XmlTag type = myTag.findFirstSubTag(
      ((namespacePrefix.length() > 0)?namespacePrefix+":":"")+"simpleType"
    );

    if (type != null) {
      final EnumerationData data = getEnumeratedValuesImpl(type);
      return data != null? data.enumeratedValues:ArrayUtil.EMPTY_STRING_ARRAY;
    }

    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  static class EnumerationData {
    final String[] enumeratedValues;
    final boolean exaustive;

    EnumerationData(@NotNull String[] _values, boolean _exaustive) {
      enumeratedValues = _values;
      exaustive = _exaustive;
    }
  }

  private static EnumerationData getEnumeratedValuesImpl(final XmlTag declaration) {
    if ("boolean".equals(declaration.getAttributeValue("name"))) {
      return new EnumerationData(new String[] {"true", "false"}, true);
    }

    final HashSet<String> variants = new HashSet<String>();
    final boolean exaustive = XmlUtil.collectEnumerationValues(declaration, variants);

    if (variants.size() > 0) {
      return new EnumerationData(variants.toArray(new String[variants.size()]), exaustive);
    }
    return null;
  }

  public String getName(PsiElement context) {
    final String form = myTag.getAttributeValue("form");
    boolean isQualifiedAttr = QUALIFIED_ATTR_VALUE.equals(form);

    final XmlTag rootTag = (((XmlFile) myTag.getContainingFile())).getDocument().getRootTag();
    String targetNs = rootTag.getAttributeValue("targetNamespace");
    XmlTag contextTag = (XmlTag)context;
    String name = getName();

    boolean attributeShouldBeQualified = false;

    if (targetNs != null && !contextTag.getNamespace().equals(targetNs)) {
      final XmlElementDescriptor xmlElementDescriptor = contextTag.getDescriptor();
      
      if (xmlElementDescriptor instanceof XmlElementDescriptorImpl) {
        final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)xmlElementDescriptor;
        final TypeDescriptor type = elementDescriptor.getType();

        if (type instanceof ComplexTypeDescriptor) {
          final ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
          attributeShouldBeQualified = typeDescriptor.canContainAttribute(name, targetNs) != ComplexTypeDescriptor.CanContainAttributeType.CanNotContain;
        }
      }
    }

    if (targetNs != null &&
        ( isQualifiedAttr ||
          QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("attributeFormDefault")) ||
          attributeShouldBeQualified
        )
      ) {
      final String prefixByNamespace = contextTag.getPrefixByNamespace(targetNs);
      if (prefixByNamespace!= null && prefixByNamespace.length() > 0) {
        name = prefixByNamespace + ":" + name;
      }
    }

    return name;
  }

  public void setName(String name) throws IncorrectOperationException {
    NamedObjectDescriptor.setName(myTag, name);
  }
}
