/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

public class JavaStubElementTypes {

  public static final JavaClassElementType CLASS = new JavaClassElementType();
  public static final JavaModifierListElementType MODIFIER_LIST = new JavaModifierListElementType();
  public static final JavaMethodElementType METHOD = new JavaMethodElementType();
  public static final JavaFieldStubElementType FIELD = new JavaFieldStubElementType();
  public static final JavaAnnotationElementType ANNOTATION = new JavaAnnotationElementType();
  public static final JavaClassReferenceListElementType REFLIST = new JavaClassReferenceListElementType();
  public static final JavaParameterElementType PARAMETER = new JavaParameterElementType();
  public static final JavaParameterListElementType PARAMETER_LIST = new JavaParameterListElementType();
  public static final JavaTypeParameterElementType TYPE_PARAMETER = new JavaTypeParameterElementType();
  public static final JavaTypeParameterListElementType TYPE_PARAMETER_LIST = new JavaTypeParameterListElementType();
  public static final JavaClassInitializerElementType CLASS_INITIALIZER = new JavaClassInitializerElementType();
}