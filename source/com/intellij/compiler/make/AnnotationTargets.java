package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
public interface AnnotationTargets {
  /** Class, interface or enum declaration */
  int TYPE = 0x1;

  /** Field declaration (includes enum constants) */
  int FIELD = 0x2;

  /** Method declaration */
  int METHOD = 0x4;

  /** Parameter declaration */
  int PARAMETER = 0x8;

  /** Constructor declaration */
  int CONSTRUCTOR = 0x10;

  /** Local variable declaration */
  int LOCAL_VARIABLE = 0x20;

  /** Annotation type declaration */
  int ANNOTATION_TYPE = 0x40;

  /** Package declaration */
  int PACKAGE = 0x80;

  int ALL = TYPE | FIELD | METHOD | PARAMETER | CONSTRUCTOR | LOCAL_VARIABLE | ANNOTATION_TYPE | PACKAGE;
}
