package com.intellij.psi.impl.source.javadoc;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author mike
 */
public class PsiDocMethodOrFieldRef extends CompositePsiElement implements PsiDocTagValue {
  public PsiDocMethodOrFieldRef() {
    super(DOC_METHOD_OR_FIELD_REF);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocTagValue(this);
  }

  public PsiReference getReference() {
    final PsiElement scope = getScope();
    final PsiElement element = getNameElement();
    if (element == null) return new MyReference(null);
    final String name = element.getText();


    PsiType[] signature = getSignature();

    final PsiMethod[] methods = getAllMethods(scope, this);

    nextMethod: for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (!method.getName().equals(name)) continue;

      if (signature == null) {
        return new MyReference(method);
      }
      else {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != signature.length) continue;
        for (int j = 0; j < parameters.length; j++) {
          PsiParameter parameter = parameters[j];
          if (!TypeConversionUtil.erasure(parameter.getType()).equals(signature[j])) continue nextMethod;
        }

        return new MyReference(method){
          public Object[] getVariants(){
            final List lst = new ArrayList();
            for(int i = 0; i < methods.length; i++){
              final PsiMethod method = methods[i];
              if(name.equals(method.getName())){
                lst.add(method);
              }
            }
            return lst.toArray();
          }
        };
      }
    }

    if (signature != null) return new MyReference(null);

    final PsiVariable[] vars = getAllVariables(scope, this);
    for (int i = 0; i < vars.length; i++) {
      PsiVariable var = vars[i];
      if (!var.getName().equals(name)) continue;
      return new MyReference(var);
    }

    return new MyReference(null);
  }

  public static PsiVariable[] getAllVariables(PsiElement scope, PsiElement place) {
    final List result = new ArrayList();
    PsiScopesUtil.processScope(scope, new FilterScopeProcessor(new ClassFilter(PsiVariable.class), place, result), PsiSubstitutor.UNKNOWN, null, place);
    return (PsiVariable[]) result.toArray(new PsiVariable[result.size()]);
  }

  public static PsiMethod[] getAllMethods(PsiElement scope, PsiElement place) {
    final List result = new ArrayList();
    PsiScopesUtil.processScope(scope, new FilterScopeProcessor(new ClassFilter(PsiMethod.class), place, result), PsiSubstitutor.UNKNOWN, null, place);
    return (PsiMethod[]) result.toArray(new PsiMethod[result.size()]);
  }

  public int getTextOffset() {
    final PsiElement element = getNameElement();

    if (element != null) {
      return element.getTextRange().getStartOffset();
    }

    return getTextRange().getEndOffset();
  }

  public PsiElement getNameElement() {
    final TreeElement sharp = TreeUtil.findChild(this, DOC_TAG_VALUE_SHARP_TOKEN);
    if (sharp == null) return null;
    return SourceTreeToPsiMap.treeElementToPsi(sharp).getNextSibling();
  }


  private PsiType[] getSignature() {
    PsiElement element = getNameElement().getNextSibling();

    while (element != null && !(element instanceof PsiDocTagValue)) {
      element = element.getNextSibling();
    }

    if (element == null) return null;

    List types = new ArrayList();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiTypeElement) {
        PsiTypeElement type = (PsiTypeElement)child;
        types.add(type.getType());
      }
    }

    return (PsiType[])types.toArray(new PsiType[types.size()]);
  }

  private PsiElement getScope(){
    ChameleonTransforming.transformChildren(this);
    if (firstChild.getElementType() == ElementType.JAVA_CODE_REFERENCE || firstChild.getElementType() == ElementType.REFERENCE_EXPRESSION) {
      PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(firstChild);
      final PsiElement referencedElement = referenceElement.resolve();
      if (referencedElement instanceof PsiClass) {
        return referencedElement;
      }
    }
    final PsiElement place = SourceTreeToPsiMap.treeElementToPsi(TreeUtil.findParent(this, TreeElement.CLASS));
    if(place == null)
      return getContainingFile();
    return place;
  }

  private class MyReference implements PsiReference {
    private final PsiElement myReferencee;

    public MyReference(PsiElement referencee) {
      myReferencee = referencee;
    }

    public PsiElement resolve() {
      return myReferencee;
    }

    public Object[] getVariants(){
      final PsiElement scope = getScope();
      final List vars = new ArrayList();
      vars.addAll(Arrays.asList(getAllMethods(scope, PsiDocMethodOrFieldRef.this)));
      vars.addAll(Arrays.asList(getAllVariables(scope, PsiDocMethodOrFieldRef.this)));
      return vars.toArray();
    }

    public boolean isSoft(){
      return false;
    }

    public String getCanonicalText() {
      return getNameElement().getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement element = getNameElement();
      final TreeElement treeElement = SourceTreeToPsiMap.psiElementToTree(element);
      final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
      LeafElement newToken = Factory.createSingleLeafElement(DOC_TAG_VALUE_TOKEN, newElementName.toCharArray(), 0, newElementName.length(), charTableByTree, getManager());
      treeElement.getTreeParent().replaceChildInternal(SourceTreeToPsiMap.psiElementToTree(element), newToken);
      return SourceTreeToPsiMap.treeElementToPsi(newToken);
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      if (isReferenceTo(element)) return PsiDocMethodOrFieldRef.this;
      final String name = getNameElement().getText();
      final String newName;

      final PsiMethod method;
      final PsiField field;
      final boolean hasSignature;
      final PsiClass containingClass;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        hasSignature = getSignature() != null;
        containingClass = method.getContainingClass();
        newName = method.getName();
      } else if (element instanceof PsiField) {
        field = (PsiField) element;
        hasSignature = false;
        containingClass = field.getContainingClass();
        method = null;
        newName = field.getName();
      } else {
        throw new IncorrectOperationException();
      }


      if (getFirstChild() instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) getFirstChild();
        referenceElement.bindToElement(containingClass);
      }
      else {
        if (!PsiTreeUtil.isAncestor(containingClass, PsiDocMethodOrFieldRef.this, true)) {
          final PsiReferenceExpression ref = containingClass.getManager().getElementFactory().createReferenceExpression(containingClass);
          addAfter(ref, null);
        }
      }

      if (hasSignature || !name.equals(newName)) {
        String text = getText();

        StringBuffer newText = new StringBuffer("/** @see ");
        if (name.equals(newName)) { // hasSignature is true here, so we can search for '('
          newText.append(text.substring(0, text.indexOf('(')));
        }
        else {
          final int sharpIndex = text.indexOf('#');
          if (sharpIndex >= 0) {
            newText.append(text.substring(0, sharpIndex + 1));
          }
          newText.append(newName);
        }
        if (hasSignature) {
          newText.append('(');
          PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (i > 0) newText.append(",");
            newText.append(parameter.getType().getCanonicalText());
          }
          newText.append(')');
        }
        newText.append("*/");

        PsiComment comment = containingClass.getManager().getElementFactory().createCommentFromText(newText.toString(), null);
        PsiElement tag = PsiTreeUtil.getChildOfType(comment, PsiDocTag.class);
        PsiElement ref = PsiTreeUtil.getChildOfType(tag, PsiDocMethodOrFieldRef.class);
        return replace(ref);
      }

      return PsiDocMethodOrFieldRef.this;
    }

    public boolean isReferenceTo(PsiElement element) {
      return element.getManager().areElementsEquivalent(element, resolve());
    }

    public TextRange getRangeInElement() {
      final TreeElement sharp = TreeUtil.findChild(PsiDocMethodOrFieldRef.this, DOC_TAG_VALUE_SHARP_TOKEN);
      if (sharp == null) return new TextRange(0, getTextLength());
      final PsiElement nextSibling = SourceTreeToPsiMap.treeElementToPsi(sharp).getNextSibling();
      if(nextSibling != null){
        final int startOffset = nextSibling.getTextRange().getStartOffset() - getTextRange().getStartOffset();
        int endOffset = nextSibling.getTextRange().getEndOffset() - getTextRange().getStartOffset();
        final PsiElement nextParSibling = nextSibling.getNextSibling();
        if(nextParSibling != null && "(".equals(nextParSibling.getText())){
          endOffset ++;
          PsiElement nextElement = nextParSibling.getNextSibling();
          if(nextElement != null && SourceTreeToPsiMap.psiElementToTree(nextElement).getElementType() == DOC_TAG_VALUE_TOKEN){
            endOffset += nextElement.getTextLength();
            nextElement = nextElement.getNextSibling();
          }
          if(nextElement != null && ")".equals(nextElement.getText())){
            endOffset ++;
          }
        }
        return new TextRange(startOffset, endOffset);
      }
      return new TextRange(getTextLength(), getTextLength());
    }

    public PsiElement getElement() {
      return PsiDocMethodOrFieldRef.this;
    }
  }
}
