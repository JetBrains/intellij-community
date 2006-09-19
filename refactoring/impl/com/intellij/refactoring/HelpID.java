
package com.intellij.refactoring;

import com.intellij.psi.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class HelpID {
  private static final String RENAME_PACKAGE   = "refactoring.renamePackage";
  private static final String RENAME_CLASS     = "refactoring.renameClass";
  private static final String RENAME_METHOD    = "refactoring.renameMethod";
  private static final String RENAME_FIELD     = "refactoring.renameField";
  private static final String RENAME_VARIABLE  = "refactoring.renameVariable";
  private static final String RENAME_PARAMETER = "refactoring.renameParameter";

  private static final String MOVE_PACKAGE = "refactoring.movePackage";
  private static final String MOVE_CLASS   = "refactoring.moveClass";

  public static final String INTRODUCE_VARIABLE = "refactoring.introduceVariable";
  public static final String INTRODUCE_FIELD = "refactoring.introduceField";
  public static final String INTRODUCE_CONSTANT = "refactoring.introduceConstant";
  public static final String EXTRACT_METHOD     = "refactoring.extractMethod";
  public static final String EXTRACT_INCLUDE    = "refactoring.extractInclude";

  public static final String ANONYMOUS_TO_INNER = "refactoring.convertAnonymous";
  public static final String LOCAL_TO_FIELD     = "refactoring.convertLocal";
  public static final String CHANGE_SIGNATURE   = "refactoring.changeSignature";
  public static final String ENCAPSULATE_FIELDS = "refactoring.encapsulateFields";
  public static final String EXTRACT_INTERFACE  = "refactoring.extractInterface";
  public static final String EXTRACT_SUPERCLASS = "refactoring.extractSuperclass";
  public static final String MOVE_INNER_UPPER   = "refactoring.moveInner";
  public static final String REPLACE_TEMP_WITH_QUERY = "refactoring.replaceTemp";
  public static final String MOVE_MEMBERS       = "refactoring.moveMembers";
  public static final String INLINE_METHOD      = "refactoring.inlineMethod";
  public static final String INLINE_VARIABLE    = "refactoring.inlineVariable";
  public static final String INLINE_FIELD       = "refactoring.inlineField";
  public static final String INLINE_INCLUDE     = "refactoring.inlineInclude";

  public static final String MIGRATION          = "refactoring.migration";

  public static final String COPY_CLASS         = "refactoring.copyClass";

  public static final String MAKE_METHOD_STATIC       = "refactoring.makeMethodStatic";
  public static final String MAKE_METHOD_STATIC_SIMPLE  = "refactoring.makeMethodStatic";

  public static final String INTRODUCE_PARAMETER        = "refactoring.introduceParameter";
  public static final String TURN_REFS_TO_SUPER         = "refactoring.useInterface";
  public static final String MEMBERS_PULL_UP            = "refactoring.pullMembersUp";
  public static final String MEMBERS_PUSH_DOWN          = "refactoring.pushMembersDown";
  public static final String INHERITANCE_TO_DELEGATION        = "refactoring.replaceInheritWithDelegat";
  public static final String REPLACE_CONSTRUCTOR_WITH_FACTORY = "refactoring.replaceConstrWithFactory";
  public static final String SAFE_DELETE                      = "refactoring.safeDelete";
  public static final String SAFE_DELETE_OVERRIDING           = "refactoring.safeDelete.overridingMethods";
  public static final String EJB_RENAME                 = "refactoring.rename.ejbRename";
  public static final String TYPE_COOK                  = "refactoring.generify";
  public static final String TYPE_MIGRATION             = "refactoring.migrateType";   
  public static final String CONVERT_TO_INSTANCE_METHOD = "refactoring.convertToInstanceMethod";
  public static final String METHOD_DUPLICATES          = "refactoring.replaceMethodCodeDuplicates";
  public static final String CHANGE_CLASS_SIGNATURE     = "refactoring.changeClassSignature";
  public static final String MOVE_INSTANCE_METHOD       = "refactoring.moveInstMethod";
  public static final String INVERT_BOOLEAN             = "refactoring.invert.boolean";

  public static String getRenameHelpID(PsiElement element) {
    String helpID = null;
    if (element instanceof PsiDirectory){
      helpID = HelpID.RENAME_PACKAGE;
    }
    else if (element instanceof PsiClass){
      helpID = HelpID.RENAME_CLASS;
    }
    else if (element instanceof PsiMethod){
      helpID = HelpID.RENAME_METHOD;
    }
    else if (element instanceof PsiField){
      helpID = HelpID.RENAME_FIELD;
    }
    else if (element instanceof PsiLocalVariable){
      helpID = HelpID.RENAME_VARIABLE;
    }
    else if (element instanceof PsiParameter){
      helpID = HelpID.RENAME_PARAMETER;
    }
    return helpID;
  }

  public static String getMoveHelpID(PsiElement element) {
    if (element instanceof PsiPackage){
      return MOVE_PACKAGE;
    }
    else if (element instanceof PsiClass){
      return MOVE_CLASS;
    }
    else{
      return null;
    }
  }
}
