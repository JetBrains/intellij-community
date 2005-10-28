package com.intellij.psi.impl.migration;

import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * @author dsl
 */
public class MigrationClassImpl extends LightElement implements PsiClass{
  private final String myQualifiedName;
  private final String myName;
  private final PsiMigrationImpl myMigration;

  MigrationClassImpl(PsiMigrationImpl migration, String qualifiedName) {
    super(migration.getManager());
    myMigration = migration;
    myQualifiedName = qualifiedName;
    myName = PsiNameHelper.getShortClassName(myQualifiedName);
  }

  public String toString() {
    return "MigrationClass:" + myQualifiedName;
  }

  public String getText() {
    return "class " + myName + " {}";
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitClass(this);
  }

  public PsiElement copy() {
    return new MigrationClassImpl(myMigration, myQualifiedName);
  }

  public boolean isValid() {
    return myMigration.isValid();
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  public PsiReferenceList getExtendsList() {
    return null;
  }


  public PsiReferenceList getImplementsList() {
    return null;
  }

  public PsiClassType[] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public PsiClass getSuperClass() {
    return myManager.findClass("java.lang.Object", GlobalSearchScope.allScope(myManager.getProject()));
  }

  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiClass[] getSupers() {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiClassType[] getSuperTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public PsiClass getContainingClass() {
    return null;
  }

  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptySet();
  }

  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public PsiField[] getAllFields() {
    return PsiField.EMPTY_ARRAY;
  }

  public PsiMethod[] getAllMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    return null;
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return null;
  }

  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return new ArrayList<Pair<PsiMethod,PsiSubstitutor>>();
  }

  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return new ArrayList<Pair<PsiMethod,PsiSubstitutor>>();
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return null;
  }

  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  public boolean hasTypeParameters() {
    return false;
  }

  public PsiJavaToken getLBrace() {
    return null;
  }

  public PsiJavaToken getRBrace() {
    return null;
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  // very special method!
  public PsiElement getScope() {
    return null;
  }

  public boolean isInheritor(PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public PomMemberOwner getPom() {
    //TODO:
    return null;
  }

  public String getName() {
    return myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(String name) {
    return PsiModifier.PUBLIC.equals(name);
  }

  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public PsiMetaData getMetaData() {
    return null;
  }

  public boolean isMetaEnough() {
    return false;
  }
}
