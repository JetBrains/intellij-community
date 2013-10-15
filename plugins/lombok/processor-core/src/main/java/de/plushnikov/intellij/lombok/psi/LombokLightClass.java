package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LombokLightClass extends LightElement implements PsiClass {
  private String myQualifiedName;
  private PsiReferenceList myExtendsList;
  private PsiReferenceList myImplementsList;
  private PsiClassType[] myExtendsListTypes = new PsiClassType[0];
  private PsiClassType[] myImplementsListTypes = new PsiClassType[0];
  private PsiClass mySuperClass;
  private PsiClass[] myInterfaces;
  private PsiClass[] mySupers;
  private PsiClassType[] mySuperTypes = new PsiClassType[0];
  private PsiField[] myFields = new PsiField[0];
  private PsiMethod[] myMethods = new PsiMethod[0];
  private PsiMethod[] myConstructors = new PsiMethod[0];
  private PsiClass[] myInnerClasses = new PsiClass[0];
  private PsiClass myContainingClass;
  private PsiClassInitializer[] myClassInitializers = new PsiClassInitializer[0];
  private PsiTypeParameter[] myTypeParameters = new PsiTypeParameter[0];
  private PsiModifierList myModifierList;
  private boolean myIsInterface = false;
  private boolean myIsAnnotationType = false;
  private boolean myIsEnum = false;
  private boolean myIsDeprecated = false;

  public LombokLightClass(PsiManager manager, Language language) {
    super(manager, language);
    myModifierList = new LombokLightModifierListImpl(manager, JavaLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return "LombokLightClass:" + getName();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public boolean isInterface() {
    return myIsInterface;
  }

  @Override
  public boolean isAnnotationType() {
    return myIsAnnotationType;
  }

  @Override
  public boolean isEnum() {
    return myIsEnum;
  }

  @Nullable
  @Override
  public PsiReferenceList getExtendsList() {
    return myExtendsList;
  }

  @Nullable
  @Override
  public PsiReferenceList getImplementsList() {
    return myImplementsList;
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes() {
    return myExtendsListTypes;
  }

  @NotNull
  @Override
  public PsiClassType[] getImplementsListTypes() {
    return myImplementsListTypes;
  }

  @Nullable
  @Override
  public PsiClass getSuperClass() {
    return mySuperClass;
  }

  @Override
  public PsiClass[] getInterfaces() {
    return myInterfaces;
  }

  @NotNull
  @Override
  public PsiClass[] getSupers() {
    return mySupers;
  }

  @NotNull
  @Override
  public PsiClassType[] getSuperTypes() {
    return mySuperTypes;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    return myFields;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethods;
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    return myConstructors;
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return myInnerClasses;
  }

  @NotNull
  @Override
  public PsiClassInitializer[] getInitializers() {
    return myClassInitializers;
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return myFields;
  }

  @NotNull
  @Override
  public PsiMethod[] getAllMethods() {
    return myMethods;
  }

  @NotNull
  @Override
  public PsiClass[] getAllInnerClasses() {
    return myInnerClasses;
  }

  @Nullable
  @Override
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    for (PsiField psiField : getAllFields()) {
      if (psiField.getName().equals(name)) {
        return psiField;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    for (PsiMethod psiMethod : getAllMethods()) {
      if (psiMethod.isEquivalentTo(patternMethod)) {
        return psiMethod;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    // TODO
    return new PsiMethod[0];
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    // TODO
    return new PsiMethod[0];
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases) {
    // TODO
    return new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    // TODO
    return new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
  }

  @Nullable
  @Override
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getLBrace() {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getRBrace() {
    return null;
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement getScope() {
    return null;
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return baseClass.equals(getSuperClass());
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return false;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @NotNull
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return new ArrayList<HierarchicalMethodSignature>();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    myQualifiedName = name;
    return this;
  }

  @Override
  public String getName() {
    return myQualifiedName;
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return myIsDeprecated;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Nullable
  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @NotNull
  @Override
  public PsiTypeParameter[] getTypeParameters() {
    return myTypeParameters;
  }

  @NotNull
  @Override
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @NotNull String name) {
    return false;
  }

  public void setMyContainingClass(PsiClass myContainingClass) {
    this.myContainingClass = myContainingClass;
  }
}