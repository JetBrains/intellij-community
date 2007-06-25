package com.intellij.refactoring;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "RefactoringSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RefactoringSettings implements PersistentStateComponent<RefactoringSettings> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.RefactoringSettings");
  // properties should be public in order to get saved by DefaultExternalizable implementation

  //public boolean RENAME_PREVIEW_USAGES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FILE = true;

  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_FOR_TEXT_FOR_CLASS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_SEARCH_FOR_TEXT_FOR_FILE = true;
  //public boolean RENAME_SEARCH_IN_NONJAVA_FOR_METHOD;
  //public boolean RENAME_SEARCH_IN_NONJAVA_FOR_FIELD;
  //public boolean RENAME_SEARCH_IN_NONJAVA_FOR_VARIABLE;

  //public boolean ENCAPSULATE_FIELDS_PREVIEW_USAGES = true;
  public boolean ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE = true;

  public boolean EXTRACT_INTERFACE_PREVIEW_USAGES = true;

  public boolean MOVE_PREVIEW_USAGES = true;
  public boolean MOVE_SEARCH_IN_COMMENTS = true;
  public boolean MOVE_SEARCH_FOR_TEXT = true;


  //public boolean INLINE_METHOD_PREVIEW_USAGES = true;
  //public boolean INLINE_FIELD_PREVIEW_USAGES = true;

  //public boolean CHANGE_SIGNATURE_PREVIEW_USAGES = true;
  public boolean CHANGE_CLASS_SIGNATURE_PREVIEW_USAGES = true;

  public boolean MOVE_INNER_PREVIEW_USAGES = true;

  //public boolean TYPE_COOK_PREVIEW_USAGES = true;
  public boolean TYPE_COOK_DROP_CASTS = true;
  public boolean TYPE_COOK_PRESERVE_RAW_ARRAYS = true;
  public boolean TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW = true;
  public boolean TYPE_COOK_EXHAUSTIVE = false;
  public boolean TYPE_COOK_COOK_OBJECTS = false;
  public boolean TYPE_COOK_PRODUCE_WILDCARDS = false;

  public boolean TYPE_MIGRATION_PREVIEW_USAGES = true;

  //public boolean MAKE_METHOD_STATIC_PREVIEW_USAGES;
  //public boolean INTRODUCE_PARAMETER_PREVIEW_USAGES;
  public int INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS;
  public int EXTRACT_INTERFACE_JAVADOC;
  public int EXTRACT_SUPERCLASS_JAVADOC;
  public boolean TURN_REFS_TO_SUPER_PREVIEW_USAGES;
  public boolean INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE;
  public String INTRODUCE_FIELD_VISIBILITY;
  public int PULL_UP_MEMBERS_JAVADOC;
  public boolean PUSH_DOWN_PREVIEW_USAGES;
  public boolean INLINE_METHOD_THIS;
  public boolean INLINE_FIELD_THIS;
  //public boolean INHERITANCE_TO_DELEGATION_PREVIEW_USAGES;
  public boolean INHERITANCE_TO_DELEGATION_DELEGATE_OTHER;
  //public boolean REPLACE_CONSTRUCTOR_WITH_FACTORY_PREVIEW_USAGES;
  public boolean SAFE_DELETE_SEARCH_IN_COMMENTS = true;
  public boolean SAFE_DELETE_SEARCH_IN_NON_JAVA = true;
  public boolean SAFE_DELETE_WHEN_DELETE = true;
  public String INTRODUCE_CONSTANT_VISIBILITY;
  public boolean CONVERT_TO_INSTANCE_METHOD_PREVIEW_USAGES = true;

  public Boolean INTRODUCE_LOCAL_CREATE_FINALS = null;
  public Boolean INTRODUCE_PARAMETER_CREATE_FINALS = null;

  public boolean INLINE_CLASS_SEARCH_IN_COMMENTS = true;
  public boolean INLINE_CLASS_SEARCH_IN_NON_JAVA = true;

  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_INHERITORS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean RENAME_VARIABLES = true;

  public static RefactoringSettings getInstance() {
    return ServiceManager.getService(RefactoringSettings.class);
  }

  public boolean isToSearchInCommentsForRename(PsiElement element) {
    if (element instanceof PsiDirectory) {
      element = ((PsiDirectory)element).getPackage();
      if (element == null) return false;
    }
    if (element instanceof PsiPackage){
      return RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
    }
    else if (element instanceof PsiClass){
      return RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
    }
    else if (element instanceof PsiMethod){
      return RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    }
    else if (element instanceof PsiField){
      return RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
    }
    else if (element instanceof PsiVariable){
      return RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
    }
    else if (element instanceof XmlAttributeValue) {
      return false;
    }
    else if (element instanceof PsiFileSystemItem) {
      return RENAME_SEARCH_IN_COMMENTS_FOR_FILE;
    }
    else if (element instanceof PsiNamedElement) {
      return false;
    }
    else{
      LOG.assertTrue(false);
      return false;
    }
  }

  public boolean isToSearchForTextOccurencesForRename(PsiElement element) {
    if (element instanceof PsiDirectory) {
      element = ((PsiDirectory)element).getPackage();
    }
    if (element instanceof PsiPackage){
      return RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
    }
    else if (element instanceof PsiClass){
      return RENAME_SEARCH_FOR_TEXT_FOR_CLASS;
    }
    else if (element instanceof PsiFileSystemItem){
      return RENAME_SEARCH_FOR_TEXT_FOR_FILE;
    }
    /*
    else if (element instanceof PsiMethod){
      return RENAME_SEARCH_IN_NONJAVA_FOR_METHOD;
    }
    else if (element instanceof PsiField){
      return RENAME_SEARCH_IN_NONJAVA_FOR_FIELD;
    }
    else if (element instanceof PsiVariable){
      return RENAME_SEARCH_IN_NONJAVA_FOR_VARIABLE;
    }
    */
    else{
      /*
      LOG.assertTrue(false);
      */
      return false;
    }
  }

  public void setToSearchInCommentsForRename(PsiElement element, boolean value) {
    if (element instanceof PsiDirectory) {
      final PsiPackage aPackage = ((PsiDirectory)element).getPackage();
      if (aPackage != null) {
        element = aPackage;
      }
    }
    if (element instanceof PsiPackage){
      RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = value;
    }
    else if (element instanceof PsiClass){
      RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = value;
    }
    else if (element instanceof PsiMethod){
      RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = value;
    }
    else if (element instanceof PsiField){
      RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = value;
    }
    else if (element instanceof PsiVariable){
      RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = value;
    }
    else if (element instanceof PsiFileSystemItem){
      RENAME_SEARCH_IN_COMMENTS_FOR_FILE = value;
    }
    else if (element instanceof PsiNamedElement ||
             element instanceof XmlAttributeValue) {

    }
    else{
      LOG.assertTrue(false);
    }
  }

  public void setToSearchInNonJavaFilesForRename(PsiElement element, boolean value) {
    if (element instanceof PsiDirectory) {
      final PsiPackage aPackage = ((PsiDirectory)element).getPackage();
      if (aPackage != null) {
        element = aPackage;
      }
    }
    if (element instanceof PsiPackage){
      RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = value;
    }
    else if (element instanceof PsiClass){
      RENAME_SEARCH_FOR_TEXT_FOR_CLASS = value;
    }
    else if (element instanceof PsiMethod){
      //RENAME_SEARCH_IN_NONJAVA_FOR_METHOD = value;
    }
    else if (element instanceof PsiField){
      //RENAME_SEARCH_IN_NONJAVA_FOR_FIELD = value;
    }
    else if (element instanceof PsiVariable){
      //RENAME_SEARCH_IN_NONJAVA_FOR_VARIABLE = value;
    }
    else if (element instanceof XmlAttributeValue) {

    }
    else if (element instanceof PsiFileSystemItem) {
      RENAME_SEARCH_FOR_TEXT_FOR_FILE = value;
    }
    else if (element instanceof PsiNamedElement) {

    }
    else{
      LOG.assertTrue(false);
    }
  }

  public boolean isToRenameInheritors(final PsiElement psiElement) {
    return RENAME_INHERITORS;
  }

  public boolean isToRenameVariables(PsiElement element) {
    return RENAME_VARIABLES;
  }

  public void setRenameInheritors(final boolean RENAME_INHERITORS) {
    this.RENAME_INHERITORS = RENAME_INHERITORS;
  }

  public void setRenameVariables(final boolean RENAME_VARIABLES) {
    this.RENAME_VARIABLES = RENAME_VARIABLES;
  }

  public RefactoringSettings getState() {
    return this;
  }

  public void loadState(RefactoringSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}