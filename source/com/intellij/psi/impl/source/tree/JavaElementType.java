package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;

public interface JavaElementType {
  //chameleons
  IElementType JAVA_FILE_TEXT = new IJavaElementType("JAVA_FILE_TEXT");
  IElementType IMPORT_LIST_TEXT = new IJavaElementType("IMPORT_LIST_TEXT");
  IElementType CODE_BLOCK_TEXT = new IJavaElementType("CODE_BLOCK_TEXT");
  IElementType EXPRESSION_TEXT = new IJavaElementType("EXPRESSION_TEXT");
  IElementType JAVA_FILE = new IJavaElementType("JAVA_FILE");
  IElementType TYPE_PARAMETER = new IJavaElementType("TYPE_PARAMETER");
  IElementType TYPE_PARAMETER_LIST = new IJavaElementType("TYPE_PARAMETER_LIST");
  IElementType STATEMENTS = new IJavaElementType("STATEMENTS");
  IElementType TYPE_TEXT = new IJavaElementType("TYPE_TEXT");

  IElementType ERROR_ELEMENT = new IJavaElementType("ERROR_ELEMENT");

  IElementType JAVA_CODE_REFERENCE = new IJavaElementType("JAVA_CODE_REFERENCE");

  IElementType PACKAGE_STATEMENT = new IJavaElementType("PACKAGE_STATEMENT");
  IElementType CLASS = new IJavaElementType("CLASS");
  IElementType ANONYMOUS_CLASS = new IJavaElementType("ANONYMOUS_CLASS");
  IElementType ENUM_CONSTANT_INITIALIZER = new IJavaElementType("ENUM_CONSTANT_INITIALIZER");
  IElementType IMPORT_LIST = new IJavaElementType("IMPORT_LIST");
  IElementType IMPORT_STATEMENT = new IJavaElementType("IMPORT_STATEMENT");
  IElementType IMPORT_STATIC_STATEMENT = new IJavaElementType("IMPORT_STATIC_STATEMENT");
  IElementType IMPORT_STATIC_REFERENCE = new IJavaElementType("IMPORT_STATIC_REFERENCE");
  IElementType MODIFIER_LIST = new IJavaElementType("MODIFIER_LIST");
  IElementType EXTENDS_LIST = new IJavaElementType("EXTENDS_LIST");
  IElementType IMPLEMENTS_LIST = new IJavaElementType("IMPLEMENTS_LIST");
  IElementType CODE_BLOCK = new IJavaElementType("CODE_BLOCK");
  IElementType FIELD = new IJavaElementType("FIELD");
  IElementType ENUM_CONSTANT = new IJavaElementType("ENUM_CONSTANT");
  IElementType METHOD = new IJavaElementType("METHOD");
  IElementType LOCAL_VARIABLE = new IJavaElementType("LOCAL_VARIABLE");
  IElementType CLASS_INITIALIZER = new IJavaElementType("CLASS_INITIALIZER");
  IElementType PARAMETER = new IJavaElementType("PARAMETER");
  IElementType TYPE = new IJavaElementType("TYPE");
  IElementType PARAMETER_LIST = new IJavaElementType("PARAMETER_LIST");
  IElementType EXTENDS_BOUND_LIST = new IJavaElementType("EXTENDS_BOUND_LIST");
  IElementType THROWS_LIST = new IJavaElementType("THROWS_LIST");
  IElementType REFERENCE_PARAMETER_LIST = new IJavaElementType("REFERENCE_PARAMETER_LIST");

  IElementType REFERENCE_EXPRESSION = new IJavaElementType("REFERENCE_EXPRESSION");
  IElementType LITERAL_EXPRESSION = new IJavaElementType("LITERAL_EXPRESSION");
  IElementType THIS_EXPRESSION = new IJavaElementType("THIS_EXPRESSION");
  IElementType SUPER_EXPRESSION = new IJavaElementType("SUPER_EXPRESSION");
  IElementType PARENTH_EXPRESSION = new IJavaElementType("PARENTH_EXPRESSION");
  IElementType METHOD_CALL_EXPRESSION = new IJavaElementType("METHOD_CALL_EXPRESSION");
  IElementType TYPE_CAST_EXPRESSION = new IJavaElementType("TYPE_CAST_EXPRESSION");
  IElementType PREFIX_EXPRESSION = new IJavaElementType("PREFIX_EXPRESSION");
  IElementType POSTFIX_EXPRESSION = new IJavaElementType("POSTFIX_EXPRESSION");
  IElementType BINARY_EXPRESSION = new IJavaElementType("BINARY_EXPRESSION");
  IElementType CONDITIONAL_EXPRESSION = new IJavaElementType("CONDITIONAL_EXPRESSION");
  IElementType ASSIGNMENT_EXPRESSION = new IJavaElementType("ASSIGNMENT_EXPRESSION");
  IElementType NEW_EXPRESSION = new IJavaElementType("NEW_EXPRESSION");
  IElementType ARRAY_ACCESS_EXPRESSION = new IJavaElementType("ARRAY_ACCESS_EXPRESSION");
  IElementType ARRAY_INITIALIZER_EXPRESSION = new IJavaElementType("ARRAY_INITIALIZER_EXPRESSION");
  IElementType INSTANCE_OF_EXPRESSION = new IJavaElementType("INSTANCE_OF_EXPRESSION");
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION = new IJavaElementType("CLASS_OBJECT_ACCESS_EXPRESSION");
  IElementType EMPTY_EXPRESSION = new IJavaElementType("EMPTY_EXPRESSION");

  IElementType EXPRESSION_LIST = new IJavaElementType("EXPRESSION_LIST");

  IElementType EMPTY_STATEMENT = new IJavaElementType("EMPTY_STATEMENT");
  IElementType BLOCK_STATEMENT = new IJavaElementType("BLOCK_STATEMENT");
  IElementType EXPRESSION_STATEMENT = new IJavaElementType("EXPRESSION_STATEMENT");
  IElementType EXPRESSION_LIST_STATEMENT = new IJavaElementType("EXPRESSION_LIST_STATEMENT");
  IElementType DECLARATION_STATEMENT = new IJavaElementType("DECLARATION_STATEMENT");
  IElementType IF_STATEMENT = new IJavaElementType("IF_STATEMENT");
  IElementType WHILE_STATEMENT = new IJavaElementType("WHILE_STATEMENT");
  IElementType FOR_STATEMENT = new IJavaElementType("FOR_STATEMENT");
  IElementType FOREACH_STATEMENT = new IJavaElementType("FOREACH_STATEMENT");
  IElementType DO_WHILE_STATEMENT = new IJavaElementType("DO_WHILE_STATEMENT");
  IElementType SWITCH_STATEMENT = new IJavaElementType("SWITCH_STATEMENT");
  IElementType SWITCH_LABEL_STATEMENT = new IJavaElementType("SWITCH_LABEL_STATEMENT");
  IElementType BREAK_STATEMENT = new IJavaElementType("BREAK_STATEMENT");
  IElementType CONTINUE_STATEMENT = new IJavaElementType("CONTINUE_STATEMENT");
  IElementType RETURN_STATEMENT = new IJavaElementType("RETURN_STATEMENT");
  IElementType THROW_STATEMENT = new IJavaElementType("THROW_STATEMENT");
  IElementType SYNCHRONIZED_STATEMENT = new IJavaElementType("SYNCHRONIZED_STATEMENT");
  IElementType TRY_STATEMENT = new IJavaElementType("TRY_STATEMENT");
  IElementType LABELED_STATEMENT = new IJavaElementType("LABELED_STATEMENT");
  IElementType ASSERT_STATEMENT = new IJavaElementType("ASSERT_STATEMENT");

  IElementType CATCH_SECTION = new IElementType("CATCH_SECTION");

  IElementType ANNOTATION_METHOD = new IJavaElementType("ANNOTATION_METHOD");
  IElementType ANNOTATION_ARRAY_INITIALIZER = new IJavaElementType("ANNOTATION_ARRAY_INITIALIZER");
  IElementType ANNOTATION = new IJavaElementType("ANNOTATION");
  IElementType NAME_VALUE_PAIR = new IJavaElementType("NAME_VALUE_PAIR");
  IElementType ANNOTATION_PARAMETER_LIST = new IJavaElementType("ANNOTATION_PARAMETER_LIST");
}
