/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 30, 2002
 * Time: 9:46:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.util.ArrayUtil;

public class AnyXmlAttributeDescriptor implements XmlAttributeDescriptor {
  private final String myAttributeName;

  public AnyXmlAttributeDescriptor(String attributeName) {
    myAttributeName = attributeName;
  }

  public PsiElement getDeclaration(){
    return null;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    return true;
  }

  public String getName(PsiElement context){
    return myAttributeName;
  }

  public String getName() {
    return myAttributeName;
  }

  public void init(PsiElement element){
  }

  public Object[] getDependences(){
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public String getQualifiedName() {
    return myAttributeName;
  }

  public String getDefaultName() {
    return myAttributeName;
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

  public String getDefaultValue() {
    return null;
  }

  //todo: refactor to hierarchy of value descriptor?
  public boolean isEnumerated() {
    return false;
  }

  public String[] getEnumeratedValues() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public String validateValue(XmlElement context, String value) {
    return null;
  }
}
