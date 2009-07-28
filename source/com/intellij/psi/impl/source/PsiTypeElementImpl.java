package com.intellij.psi.impl.source;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jmock.util.NotImplementedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiTypeElementImpl");
  private volatile PsiType myCachedType = null;
  private volatile PatchedSoftReference<PsiType> myCachedDetachedType = null;

  public PsiTypeElementImpl() {
    super(JavaElementType.TYPE);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedType = null;
    myCachedDetachedType = null;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiTypeElement:" + getText();
  }

  @NotNull
  public PsiType getType() {
    PsiType cachedType = myCachedType;
    if (cachedType != null) {
      return cachedType;
    }
    TreeElement element = getFirstChildNode();
    List<PsiAnnotation> typeAnnos = new ArrayList<PsiAnnotation>();
    while (element != null) {
      IElementType elementType = element.getElementType();
      if (element.getTreeNext() == null && ElementType.PRIMITIVE_TYPE_BIT_SET.contains(elementType)) {
        addTypeUseAnnotationsFromModifierList(getParent(), typeAnnos);
        PsiAnnotation[] array = typeAnnos.toArray(new PsiAnnotation[typeAnnos.size()]);

        cachedType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(element.getText(), array);
        assert cachedType != null;
      }
      else if (elementType == JavaElementType.TYPE) {
        PsiType componentType = ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(element)).getType();
        cachedType = getLastChildNode().getElementType() == JavaTokenType.ELLIPSIS ? new PsiEllipsisType(componentType)
                                                                                   : componentType.createArrayType();
      }
      else if (elementType == JavaElementType.JAVA_CODE_REFERENCE) {
        addTypeUseAnnotationsFromModifierList(getParent(), typeAnnos);
        PsiAnnotation[] array = typeAnnos.toArray(new PsiAnnotation[typeAnnos.size()]);
        cachedType = new PsiClassReferenceType((PsiJavaCodeReferenceElement)element.getPsi(), null,array);
      }
      else if (elementType == JavaTokenType.QUEST) {
        cachedType = createWildcardType();
      }
      else if (JavaTokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(elementType)) {
        element = element.getTreeNext();
        continue;
      }
      else if (elementType == JavaElementType.ANNOTATION) {
        PsiAnnotation annotation = JavaPsiFacade.getInstance(getProject()).getElementFactory().createAnnotationFromText(element.getText(), this);
        typeAnnos.add(annotation);
        element = element.getTreeNext();
        continue;
      }
      else {
        LOG.error("Unknown element type: " + elementType);
      }
      if (element.getTextLength() != 0) break;
      element = element.getTreeNext();
    }

    if (cachedType == null) cachedType = PsiType.NULL;
    myCachedType = cachedType;
    return cachedType;
  }

  public static void addTypeUseAnnotationsFromModifierList(PsiElement member, List<PsiAnnotation> typeAnnos) {
    if (!(member instanceof PsiModifierListOwner)) return;
    PsiModifierList list = ((PsiModifierListOwner)member).getModifierList();
    PsiAnnotation[] gluedAnnos = list == null ? PsiAnnotation.EMPTY_ARRAY : list.getAnnotations();
    for (PsiAnnotation anno : gluedAnnos) {
      if (AnnotationsHighlightUtil.isAnnotationApplicableTo(anno, true, "TYPE_USE")) {
        typeAnnos.add(anno);
      }
    }
  }

  public PsiType getDetachedType(@NotNull PsiElement context) {
    PatchedSoftReference<PsiType> cached = myCachedDetachedType;
    PsiType type = cached == null ? null : cached.get();
    if (type != null) return type;
    try {
      String text = StringUtil.join(getApplicableAnnotations(), new Function<PsiAnnotation, String>() {
        public String fun(PsiAnnotation psiAnnotation) {
          return psiAnnotation.getText();
        }
      }, " ") + " " + getText().trim();
      type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(text, context);
      myCachedDetachedType = new PatchedSoftReference<PsiType>(type);
    }
    catch (IncorrectOperationException e) {
      return getType();
    }
    return type;
  }

  public PsiType getTypeNoResolve(@NotNull PsiElement context) {
    try {
      String text = StringUtil.join(getAnnotations(), new Function<PsiAnnotation, String>() {
        public String fun(PsiAnnotation psiAnnotation) {
          return psiAnnotation.getText();
        }
      }, " ") + " " + getText().trim();
      return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(text, context);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return getType();
  }

  @NotNull
  private PsiType createWildcardType() {
    final PsiType temp;
    if (getFirstChildNode().getTreeNext() == null) {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    else if (getLastChildNode().getElementType() == JavaElementType.TYPE) {
      PsiTypeElement bound = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(getLastChildNode());
      ASTNode keyword = getFirstChildNode();
      while (keyword != null &&
             keyword.getElementType() != JavaTokenType.EXTENDS_KEYWORD &&
             keyword.getElementType() != JavaTokenType.SUPER_KEYWORD) {
        keyword = keyword.getTreeNext();
      }
      if (keyword != null) {
        IElementType i = keyword.getElementType();
        if (i == JavaTokenType.EXTENDS_KEYWORD) {
          temp = PsiWildcardType.createExtends(getManager(), bound.getType());
        }
        else if (i == JavaTokenType.SUPER_KEYWORD) {
          temp = PsiWildcardType.createSuper(getManager(), bound.getType());
        }
        else {
          LOG.assertTrue(false);
          temp = PsiWildcardType.createUnbounded(getManager());
        }
      }
      else {
        temp = PsiWildcardType.createUnbounded(getManager());
      }
    }
    else {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    return temp;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) return null;
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(firstChildNode)).getInnermostComponentReferenceElement();
    }
    else {
      return getReferenceElement();
    }
  }

  public PsiAnnotationOwner getOwner(PsiAnnotation annotation) {
    PsiElement next = PsiTreeUtil.skipSiblingsForward(annotation, PsiComment.class, PsiWhiteSpace.class);
    if (next != null && next.getNode().getElementType() == ElementType.LBRACKET) {
      PsiType type = getType();
      return type;  // annotation belongs to array type dimension
    }
    return this;
  }

  private PsiJavaCodeReferenceElement getReferenceElement() {
    ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
    if (ref == null) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    List<PsiAnnotation> result = null;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() != JavaElementType.ANNOTATION) continue;
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == ElementType.LBRACKET) continue; //annotation on array dimension
      if (result == null) result = new SmartList<PsiAnnotation>();
      PsiElement element = child.getPsi();
      assert element != null;
      result.add((PsiAnnotation)element);
    }

    return result== null ?  PsiAnnotation.EMPTY_ARRAY : result.toArray(new PsiAnnotation[result.size()]);
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    PsiAnnotation[] annotations = getAnnotations();

    ArrayList<PsiAnnotation> list = new ArrayList<PsiAnnotation>(Arrays.asList(annotations));
    addTypeUseAnnotationsFromModifierList(getParent(), list);

    return list.toArray(new PsiAnnotation[list.size()]);
  }

  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new NotImplementedException(); //todo
  }
}

