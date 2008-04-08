/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.stubs.SerializationManager;

public class JavaStubElementTypes {
  public static final JavaFileElementType FILE = new JavaFileElementType();
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
  public static final JavaImportListElementType IMPORT_LIST = new JavaImportListElementType();
  public static final JavaImportStatementElementType IMPORT_STATEMENT = new JavaImportStatementElementType();

  static {
    final SerializationManager sm = SerializationManager.getInstance();
    sm.registerSerializer(FILE);

    sm.registerSerializer(CLASS);
    sm.registerSerializer(MODIFIER_LIST);
    sm.registerSerializer(METHOD);
    sm.registerSerializer(FIELD);
    sm.registerSerializer(ANNOTATION);
    sm.registerSerializer(REFLIST);
    sm.registerSerializer(PARAMETER);
    sm.registerSerializer(PARAMETER_LIST);
    sm.registerSerializer(TYPE_PARAMETER);
    sm.registerSerializer(TYPE_PARAMETER_LIST);
    sm.registerSerializer(CLASS_INITIALIZER);
    sm.registerSerializer(IMPORT_LIST);
    sm.registerSerializer(IMPORT_STATEMENT);
  }
}