package com.intellij.xml.impl.schema;

import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor {
  private XmlTag myTag;
  String myUse;

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
    return "required".equals(myUse);
  }

  public boolean isFixed() {
    return myTag.getAttributeValue("fixed") != null;
  }

  public boolean hasIdType() {
    return false;
  }

  public boolean hasIdRefType() {
    return false;
  }

  public String getDefaultValue() {
    if (isFixed()) {
      return myTag.getAttributeValue("fixed");
    }

    return myTag.getAttributeValue("default");
  }

  //todo: refactor to hierarchy of value descriptor?
  public boolean isEnumerated() {
    return false;
  }

  public String[] getEnumeratedValues() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }


  public String getDefaultName() {
    final XmlTag rootTag = (((XmlFile) myTag.getContainingFile())).getDocument().getRootTag();
    if ("qualified".equals(rootTag.getAttributeValue("attributeFormDefault"))) {
      // TODO:
      return getName();
    }
    return getName();
  }
}
