package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NonNls;

import java.text.CharacterIterator;
import java.util.*;

/**
 * @author max
 */
public class ClsTypeParameterImpl extends ClsElementImpl implements PsiTypeParameter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsTypeParameterImpl");

  private String myName;
  private ClsReferenceListImpl myBoundsList;
  private PsiElement myParent;
  private int myIndex;
  private LightEmptyImplementsList myLightEmptyImplementsList;
  private final String mySignature;

  public ClsTypeParameterImpl(PsiElement parent,
                              CharacterIterator signature,
                              int index,
                              String parentSignatureAttribute) throws ClsFormatException {
    myParent = parent;
    myIndex = index;
    StringBuffer name = new StringBuffer();
    final int signatureBeginIndex = signature.getIndex();
    while (signature.current() != ':' && signature.current() != CharacterIterator.DONE) {
      name.append(signature.current());
      signature.next();
    }
    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }
    myName = name.toString();

    ArrayList<PsiJavaCodeReferenceElement> bounds = new ArrayList<PsiJavaCodeReferenceElement>();
    while (signature.current() == ':') {
      signature.next();
      PsiJavaCodeReferenceElement bound = GenericSignatureParsing.parseToplevelClassRefSignature(signature, this);
      if (bound != null && !bound.getQualifiedName().equals("java.lang.Object")) {
        bounds.add(bound);
      }
    }

    myBoundsList = new ClsReferenceListImpl(this, bounds.toArray(
                                              new PsiJavaCodeReferenceElement[bounds.size()]), PsiKeyword.EXTENDS);
    final int endIndex = signature.getIndex();
    mySignature = parentSignatureAttribute.substring(signatureBeginIndex, endIndex);
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

  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

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

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, substitutor, new HashSet(), lastParent, place, false);
  }

  public String getName() {
    return myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    myName = name;
    return this;
  }

  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public PsiReferenceList getExtendsList() {
    return myBoundsList;
  }

  public PsiReferenceList getImplementsList() {
    if (myLightEmptyImplementsList == null) {
      myLightEmptyImplementsList = new LightEmptyImplementsList(getManager());
    }
    return myLightEmptyImplementsList;
  }

  public PsiClassType[] getExtendsListTypes() {
    return myBoundsList.getReferencedTypes();
  }

  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
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

  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  public PsiClass getContainingClass() {
    return null;
  }

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

  public String toString() {
    return "PsiTypeParameter";
  }

  public String getMirrorText() {
    @NonNls StringBuffer buf = new StringBuffer();
    buf.append(myName);
    PsiJavaCodeReferenceElement[] bounds = myBoundsList.getReferenceElements();
    if (bounds.length > 0) {
      buf.append(" extends ");
      for (int i = 0; i < bounds.length; i++) {
        PsiJavaCodeReferenceElement bound = bounds[i];
        if (i > 0) buf.append("&");
        buf.append(bound.getCanonicalText());
      }
    }
    return buf.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiTypeParameter mirror = (PsiTypeParameter)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsReferenceListImpl)getExtendsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getExtendsList()));
  }

  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiTypeParameterListOwner getOwner() {
    if (myParent instanceof ClsTypeParametersListImpl) {
      final PsiElement parent = myParent.getParent();
      if (parent instanceof PsiTypeParameterListOwner) return (PsiTypeParameterListOwner)parent;
    }
    return null;
  }


  public int getIndex() {
    return myIndex;
  }

  public String getSignature() {
    return mySignature;
    /*
    StringBuffer buf = new StringBuffer();
    buf.append(myName);
    buf.append(":");
    PsiJavaCodeReferenceElement[] bounds = myBoundsList.getReferenceElements();
    if (bounds.length > 0) {
      for (int i = 0; i < bounds.length; i++) {
        buf.append(((ClsElementWithSignature)bounds[i]).getSignature());
        if (i < bounds.length - 1) {
          buf.append(":");
        }
      }
    }
    return buf.toString();
    */
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return false;
  }
}
