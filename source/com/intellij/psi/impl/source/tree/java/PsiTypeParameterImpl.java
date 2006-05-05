package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.IndexedRepositoryPsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 *  @author dsl
 */
public class PsiTypeParameterImpl extends IndexedRepositoryPsiElement implements PsiTypeParameter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl");

  private CompositeElement myParsedFromRepository;
  private PsiTypeParameterExtendsBoundsListImpl myExtendsBoundsList;
  private LightEmptyImplementsList myLightEmptyImplementsList;

  public PsiTypeParameterImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiTypeParameterImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner, int index) {
    super(manager, owner, index);
  }

  public PsiTypeParameterImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  public String getQualifiedName() {
    return null;
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

  @NotNull
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  public boolean hasTypeParameters() {
    return false;
  }

  // very special method!
  public PsiElement getScope() {
    return getParent().getParent();
  }

  public boolean isInheritor(PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public PomMemberOwner getPom() {
    //TODO:
    return null;
  }

  CompositeElement getMirrorTreeElement() {
    CompositeElement actualTree = getTreeElement();
    if (actualTree != null) {
      myParsedFromRepository = null;
      return actualTree;
    }

    if (myParsedFromRepository != null) return myParsedFromRepository;
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0) {
      String text;
      PsiElement owner = myOwner.getParent();
      if (owner instanceof PsiClass) {
        text = getRepositoryManager().getClassView().getParameterText(repositoryId, getIndex());
      }
      else if (owner instanceof PsiMethod) {
        text = getRepositoryManager().getMethodView().getTypeParameterText(repositoryId, getIndex());
      }
      else {
        LOG.error("Wrong owner");
        text = "";
      }
      try{
        PsiTypeParameter typeParameter = myManager.getElementFactory().createTypeParameterFromText(text, this);
        myParsedFromRepository = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(typeParameter);
        new DummyHolder(myManager, myParsedFromRepository, this);
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
      return myParsedFromRepository;
    }

    return calcTreeElement();
  }

  @NotNull
  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    final PsiElement parentParent = parent.getParent();
    return (PsiTypeParameterListOwner) parentParent;
  }


  public int getIndex() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0) {
      return super.getIndex();
    }
    else {
      int ret = 0;
      PsiElement element = getPrevSibling();
      while(element != null){
        if(element instanceof PsiTypeParameter)
          ret++;
        element = element.getPrevSibling();
      }
      return ret;
    }
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier) getMirrorTreeElement().findChildByRole(ChildRole.NAME);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place){
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, substitutor, new HashSet<PsiClass>(), lastParent, place, false);
  }

  public String getName() {
    return getNameIdentifier().getText();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myExtendsBoundsList = null;
    myParsedFromRepository = null;
  }

  @NotNull
  public PsiReferenceList getExtendsList() {
    if (myOwner != null) {
      if (myExtendsBoundsList == null) {
        myExtendsBoundsList = new PsiTypeParameterExtendsBoundsListImpl(myManager, this);
      }
      return myExtendsBoundsList;
    }
    else {
      return (PsiReferenceList) calcTreeElement().findChildByRoleAsPsiElement(ChildRole.EXTENDS_LIST);
    }
  }

  protected Object clone() {
    PsiTypeParameterImpl clone = (PsiTypeParameterImpl)super.clone();
    clone.myExtendsBoundsList = null;
    clone.myLightEmptyImplementsList = null;
    clone.myParsedFromRepository = null;
    return super.clone();
  }

  public PsiReferenceList getImplementsList() {
    if (myLightEmptyImplementsList == null) {
      myLightEmptyImplementsList = new LightEmptyImplementsList(myManager);
    }
    return myLightEmptyImplementsList;
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiField[] getAllFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  public void setOwnerAndIndex(SrcRepositoryPsiElement owner, int index) {
    super.setOwnerAndIndex(owner, index);
    if (myOwner == null) {
      if (myExtendsBoundsList != null) {
        myExtendsBoundsList.setOwner(this);
      }
    }
    else {
      myExtendsBoundsList = (PsiTypeParameterExtendsBoundsListImpl)bindSlave(ChildRole.EXTENDS_LIST);
    }
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @NotNull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  public PsiModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(String name) {
    return false;
  }

  public PsiJavaToken getLBrace() {
    return null;
  }

  public PsiJavaToken getRBrace() {
    return null;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitTypeParameter(this);
  }

  @NonNls
  public String toString() {
    return "PsiTypeParameter";
  }

  public PsiMetaData getMetaData(){
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough(){
    return false;
  }
}
