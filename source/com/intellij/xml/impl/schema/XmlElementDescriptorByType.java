package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

/**
 * @author ik
 */
public class XmlElementDescriptorByType extends XmlElementDescriptorImpl {
  private ComplexTypeDescriptor myType;
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlElementDescriptorByType(XmlTag instanceTag, ComplexTypeDescriptor descriptor) {
    super(instanceTag);
    myType = descriptor;
  }

  public XmlElementDescriptorByType() {}

  public String getName(PsiElement context){
    return myDescriptorTag.getName();
  }

  public ComplexTypeDescriptor getType() {
    return myType;
  }

  public String getDefaultName() {
    XmlTag rootTag = ((XmlFile)getType().getDeclaration().getContainingFile()).getDocument().getRootTag();

    if (QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("elementFormDefault"))) {
      return getQualifiedName();
    }

    return getName();
  }

  public boolean isAbstract() {
    return Boolean.valueOf(myDescriptorTag.getAttributeValue("abstract"));
  }

}
