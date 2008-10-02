package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class ClsClassImpl extends ClsRepositoryPsiElement<PsiClassStub> implements PsiClass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsClassImpl");

  private volatile Map<String, PsiField> myCachedFieldsMap = null;
  private volatile Map<String, PsiMethod[]> myCachedMethodsMap = null;
  private volatile Map<String, PsiClass> myCachedInnersMap = null;
  private PsiIdentifier myNameIdentifier;
  private PsiDocComment myDocComment;

  public ClsClassImpl(final PsiClassStub stub) {
    super(stub);
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiIdentifier name = getNameIdentifier();
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiReferenceList extendsList = getExtendsList();
    PsiReferenceList implementsList = getImplementsList();
    PsiField[] fields = getFields();
    PsiMethod[] methods = getMethods();
    PsiClass[] classes = getInnerClasses();

    int count =
      (docComment != null ? 1 : 0)
      + 1 // modifierList
      + 1 // name
      + 1 // extends list
      + 1 // implementsList
      + fields.length
      + methods.length
      + classes.length;
    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }

    children[offset++] = modifierList;
    children[offset++] = name;
    children[offset++] = extendsList;
    children[offset++] = implementsList;

    System.arraycopy(fields, 0, children, offset, fields.length);
    offset += fields.length;
    System.arraycopy(methods, 0, children, offset, methods.length);
    offset += methods.length;
    System.arraycopy(classes, 0, children, offset, classes.length);
    /*offset += classes.length;*/

    return children;
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    synchronized (PsiLock.LOCK) {
      if (myNameIdentifier == null) {
        String qName = getQualifiedName();
        String name = PsiNameHelper.getShortClassName(qName);
        if (name.length() == 0) {
          name = "_";
        }
        myNameIdentifier = new ClsIdentifierImpl(this, name);
      }
      return myNameIdentifier;
    }
  }

  @NotNull
  public String getName() {
    return getStub().getName();
  }

  @NotNull
  public PsiTypeParameterList getTypeParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST).getPsi();
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public String getQualifiedName() {
    return getStub().getQualifiedName();
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiReferenceList getExtendsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_LIST).getPsi();
  }


  @NotNull
  public PsiReferenceList getImplementsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.IMPLEMENTS_LIST).getPsi();
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
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
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @NotNull
  public PsiField[] getFields() {
    return getStub().getChildrenByType(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY);
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return getStub().getChildrenByType(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY);
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return PsiImplUtil.getConstructors(this);
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return getStub().getChildrenByType(JavaStubElementTypes.CLASS, ARRAY_FACTORY);
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    if (!checkBases) {
      if (myCachedFieldsMap == null) {
        HashMap<String, PsiField> cachedFieldsMap = new HashMap<String, PsiField>();
        final PsiField[] fields = getFields();
        for (final PsiField field : fields) {
          cachedFieldsMap.put(field.getName(), field);
        }
        myCachedFieldsMap = cachedFieldsMap;
      }
      return myCachedFieldsMap.get(name);
    }
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    if (!checkBases) {
      if (myCachedMethodsMap == null) {
        Map<String, PsiMethod[]> methodsMap = new HashMap<String, PsiMethod[]>();
        Map<String, List<PsiMethod>> cachedMethodsMap = new HashMap<String, List<PsiMethod>>();
        final PsiMethod[] methods = getMethods();
        for (final PsiMethod method : methods) {
          List<PsiMethod> list = cachedMethodsMap.get(method.getName());
          if (list == null) {
            list = new ArrayList<PsiMethod>(1);
            cachedMethodsMap.put(method.getName(), list);
          }
          list.add(method);
        }
        for (final String methodName : cachedMethodsMap.keySet()) {
          List<PsiMethod> methodList = cachedMethodsMap.get(methodName);
          methodsMap.put(methodName, methodList.toArray(new PsiMethod[methodList.size()]));
        }
        myCachedMethodsMap = methodsMap;
      }
      final PsiMethod[] psiMethods = myCachedMethodsMap.get(name);
      return psiMethods != null ? psiMethods : PsiMethod.EMPTY_ARRAY;
    }

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
    if (!checkBases) {
      if (myCachedInnersMap == null) {
        HashMap<String, PsiClass> cachedInnersMap = new HashMap<String, PsiClass>();
        final PsiClass[] classes = getInnerClasses();
        for (final PsiClass psiClass : classes) {
          cachedInnersMap.put(psiClass.getName(), psiClass);
        }
        myCachedInnersMap = cachedInnersMap;
      }
      return myCachedInnersMap.get(name);
    }
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  public boolean isDeprecated() {
    return getStub().isDeprecated();
  }

  public String getSourceFileName() {
    final String sfn = getStub().getSourceFileName();
    return sfn != null ? sfn : obtainSourceFileNameFromClassFileName();
  }

  @NonNls
  private String obtainSourceFileNameFromClassFileName() {
    final String name = getContainingFile().getName();
    int i = name.indexOf('$');
    if (i < 0) {
      i = name.indexOf('.');
      if (i < 0) {
        i = name.length();
      }
    }
    return name.substring(0, i) + ".java";
  }

  public PsiDocComment getDocComment() {
    if (!isDeprecated()) return null;

    synchronized (PsiLock.LOCK) {
      if (myDocComment == null) {
        myDocComment = new ClsDocCommentImpl(this);
      }
      return myDocComment;
    }
  }

  public PsiJavaToken getLBrace() {
    return null;
  }

  public PsiJavaToken getRBrace() {
    return null;
  }

  public boolean isInterface() {
    return getStub().isInterface();
  }

  public boolean isAnnotationType() {
    return getStub().isAnnotationType();
  }

  public boolean isEnum() {
    return getStub().isEnum();
  }

  public void appendMirrorText(final int indentLevel, @NonNls final StringBuffer buffer) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }
    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    buffer.append(isEnum() ? "enum " : isAnnotationType() ? "@interface " : isInterface() ? "interface " : "class ");
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
    ((ClsElementImpl)getTypeParameterList()).appendMirrorText(indentLevel, buffer);
    buffer.append(' ');
    if (!isEnum() && !isAnnotationType()) {
      ((ClsElementImpl)getExtendsList()).appendMirrorText(indentLevel, buffer);
      buffer.append(' ');
    }
    if (!isInterface()) {
      ((ClsElementImpl)getImplementsList()).appendMirrorText(indentLevel, buffer);
    }
    buffer.append('{');
    final int newIndentLevel = indentLevel + getIndentSize();
    PsiField[] fields = getFields();
    if (fields.length > 0) {
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < fields.length; i++) {
        PsiField field = fields[i];
        ((ClsElementImpl)field).appendMirrorText(newIndentLevel, buffer);
        if (field instanceof ClsEnumConstantImpl) {
          if (i < fields.length - 1 && fields[i + 1] instanceof ClsEnumConstantImpl) {
            buffer.append(", ");
          }
          else {
            buffer.append(";");
            if (i < fields.length - 1) {
              goNextLine(newIndentLevel, buffer);
            }
          }
        } else if (i < fields.length - 1) {
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    PsiMethod[] methods = getMethods();
    if (methods.length > 0) {
      goNextLine(newIndentLevel, buffer);
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < methods.length; i++) {
        PsiMethod method = methods[i];
        ((ClsElementImpl)method).appendMirrorText(newIndentLevel, buffer);
        if (i < methods.length - 1) {
          goNextLine(newIndentLevel, buffer);
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    PsiClass[] classes = getInnerClasses();
    if (classes.length > 0) {
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < classes.length; i++) {
        PsiClass aClass = classes[i];
        ((ClsElementImpl)aClass).appendMirrorText(newIndentLevel, buffer);
        if (i < classes.length - 1) {
          goNextLine(newIndentLevel, buffer);
          goNextLine(newIndentLevel, buffer);
        }
      }
    }
    goNextLine(indentLevel, buffer);
    buffer.append('}');
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    myMirror = element;
    PsiClass mirror = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(element);

    final PsiDocComment docComment = getDocComment();
    if (docComment != null) {
        ((ClsElementImpl)docComment).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
    if (!isAnnotationType() && !isEnum()) {
        ((ClsElementImpl)getExtendsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getExtendsList()));
    }
      ((ClsElementImpl)getImplementsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getImplementsList()));
      ((ClsElementImpl)getTypeParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeParameterList()));

    PsiField[] fields = getFields();
    PsiField[] mirrorFields = mirror.getFields();
    if (LOG.assertTrue(fields.length == mirrorFields.length)) {
      for (int i = 0; i < fields.length; i++) {
          ((ClsElementImpl)fields[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorFields[i]));
      }
    }

    PsiMethod[] methods = getMethods();
    PsiMethod[] mirrorMethods = mirror.getMethods();
    if (LOG.assertTrue(methods.length == mirrorMethods.length)) {
      for (int i = 0; i < methods.length; i++) {
          ((ClsElementImpl)methods[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorMethods[i]));
      }
    }

    PsiClass[] classes = getInnerClasses();
    PsiClass[] mirrorClasses = mirror.getInnerClasses();
    if (LOG.assertTrue(classes.length == mirrorClasses.length)) {
      for (int i = 0; i < classes.length; i++) {
          ((ClsElementImpl)classes[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorClasses[i]));
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiClass:" + getName();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, false);
  }

  public PsiElement getScope() {
    return getParent();
  }

  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public PsiClass getSourceMirrorClass() {
    PsiElement parent = getParent();
    final String name = getName();
    if (parent instanceof PsiFile) {
      PsiClassOwner fileNavigationElement = (PsiClassOwner)parent.getNavigationElement();
      for (PsiClass aClass : fileNavigationElement.getClasses()) {
        if (name.equals(aClass.getName())) return aClass;
      }
    }
    else {
      ClsClassImpl parentClass = (ClsClassImpl)parent;
      PsiClass parentSourceMirror = parentClass.getSourceMirrorClass();
      if (parentSourceMirror == null) return null;
      PsiClass[] innerClasses = parentSourceMirror.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (name.equals(innerClass.getName())) return innerClass;
      }
    }

    return null;
  }

  public PsiElement getNavigationElement() {
    PsiClass aClass = getSourceMirrorClass();
    return aClass != null ? aClass : this;
  }

  public ItemPresentation getPresentation() {
    return ClassPresentationUtil.getPresentation(this);
  }

  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }
}
