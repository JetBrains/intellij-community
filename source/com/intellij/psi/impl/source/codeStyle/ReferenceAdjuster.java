package com.intellij.psi.impl.source.codeStyle;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;

import java.util.ArrayList;

class ReferenceAdjuster implements Constants {
  private final CodeStyleSettings mySettings;
  private final ImportHelper myImportHelper;
  private final boolean myUseFqClassnamesInJavadoc;
  private final boolean myUseFqClassNames;

  public ReferenceAdjuster(CodeStyleSettings settings, boolean useFqInJavadoc, boolean useFqInCode) {
    mySettings = settings;
    myImportHelper = new ImportHelper(mySettings);
    myUseFqClassnamesInJavadoc = useFqInJavadoc;
    myUseFqClassNames = useFqInCode;
  }

  public ReferenceAdjuster(CodeStyleSettings settings) {
    this(settings, settings.USE_FQ_CLASS_NAMES_IN_JAVADOC, settings.USE_FQ_CLASS_NAMES);
  }

  public TreeElement process(TreeElement element, boolean addImports, boolean uncompleteCode) {
    IElementType elementType = element.getElementType();
    if (elementType == JAVA_CODE_REFERENCE || elementType == REFERENCE_EXPRESSION){
      if (elementType == JAVA_CODE_REFERENCE || element.getTreeParent().getElementType() == REFERENCE_EXPRESSION || uncompleteCode) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(element);
        final PsiTypeElement[] typeParameters = ref.getParameterList().getTypeParameterElements();
        for (int i = 0; i < typeParameters.length; i++) {
          process(SourceTreeToPsiMap.psiElementToTree(typeParameters[i]), addImports, uncompleteCode);
        }
        
        boolean rightKind = true;
        if (elementType == JAVA_CODE_REFERENCE){
          int kind = ((PsiJavaCodeReferenceElementImpl)element).getKind();
          rightKind =  kind == PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND ||
                       kind == PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND;
        }

        if (rightKind) {
          boolean isInsideDocComment = TreeUtil.findParent(element, ElementType.DOC_COMMENT) != null;
          boolean isShort = !((SourceJavaCodeReference)element).isQualified();
          if (!makeFQ(isInsideDocComment)){
            if (isShort) return element; // short name already, no need to change
          }
          PsiElement refElement;
          if (!uncompleteCode){
            refElement = ref.resolve();
          }
          else{
            PsiResolveHelper helper = element.getManager().getResolveHelper();
            refElement = helper.resolveReferencedClass(
              ((SourceJavaCodeReference)element).getClassNameText(),
              SourceTreeToPsiMap.treeElementToPsi(element)
            );
          }
          if (refElement instanceof PsiClass){
            if (makeFQ(isInsideDocComment)){
              String qName = ((PsiClass)refElement).getQualifiedName();
              if (qName == null) return element;
              PsiFile file = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile();
              if (ImportHelper.isImplicitlyImported(qName, file)){
                if (isShort) return element;
                return makeShortReference((CompositeElement)element, (PsiClass)refElement, addImports, uncompleteCode);
              }
              if (file instanceof PsiJavaFile){
                String thisPackageName = ((PsiJavaFile)file).getPackageName();
                if (ImportHelper.hasPackage(qName, thisPackageName)){
                  if (!isShort) {
                    return makeShortReference(
                      (CompositeElement)element,
                      (PsiClass)refElement,
                      addImports,
                      uncompleteCode
                    );
                  }
                }
              }
              return replaceReferenceWithFQ((CompositeElement)element, (PsiClass)refElement);
            }
            else{
              return makeShortReference((CompositeElement)element, (PsiClass)refElement, addImports, uncompleteCode);
            }
          }
        }
      }
    }

    if (element instanceof CompositeElement){
      ChameleonTransforming.transformChildren((CompositeElement)element);
      for(TreeElement child = ((CompositeElement)element).firstChild; child != null; child = child.getTreeNext()){
        child = process(child, addImports, uncompleteCode);
      }
    }

    return element;
  }

  private boolean makeFQ(boolean isInsideDocComment) {
    if (isInsideDocComment) {
      return myUseFqClassnamesInJavadoc;
    } else {
      return myUseFqClassNames;
    }
  }

  public void processRange(TreeElement element, int startOffset, int endOffset) {
    ArrayList<TreeElement> array = new ArrayList<TreeElement>();
    addReferencesInRange(array, element, startOffset, endOffset);
    for(int i = 0; i < array.size(); i++){
      TreeElement ref = array.get(i);
      if (SourceTreeToPsiMap.treeElementToPsi(ref).isValid()){
        process(ref, true, true);
      }
    }
  }

  private static void addReferencesInRange(ArrayList<TreeElement> array, TreeElement parent, int startOffset, int endOffset) {
    if (parent.getElementType() == ElementType.JAVA_CODE_REFERENCE || parent.getElementType() == ElementType.REFERENCE_EXPRESSION){
      array.add(parent);
      return;
    }
    if (parent instanceof CompositeElement){
      ChameleonTransforming.transformChildren((CompositeElement)parent);
      int offset = 0;
      for(TreeElement child = ((CompositeElement)parent).firstChild; child != null; child = child.getTreeNext()){
        int length = child.getTextLength();
        if (startOffset <= offset + length && offset <= endOffset){
          if (startOffset <= offset && offset + length <= endOffset){
            array.add(child);
          }
          addReferencesInRange(array, child, startOffset - offset, endOffset - offset);
        }
        offset += length;
      }
    }
  }

  private CompositeElement makeShortReference(
    CompositeElement reference,
    PsiClass refClass,
    boolean addImports,
    boolean uncompleteCode
    ) {
    if (refClass.getContainingClass() != null){
      PsiClass parentClass = refClass.getContainingClass();
      PsiJavaCodeReferenceElement psiReference = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(reference);
      PsiManager manager = parentClass.getManager();
      if (manager.getResolveHelper().isAccessible(refClass, psiReference, null)) {
        for(TreeElement parent = reference.getTreeParent(); parent != null; parent = parent.getTreeParent()){
          PsiElement parentPsi = SourceTreeToPsiMap.treeElementToPsi(parent);
          if (parentPsi instanceof PsiClass){
            PsiClass inner = ((PsiClass)parentPsi).findInnerClassByName(psiReference.getReferenceName(), true);
            if (inner != null) {
              if (inner == refClass) return replaceReferenceWithShort(reference);
              return reference;
            }
            if (InheritanceUtil.isInheritorOrSelf((PsiClass)parentPsi, parentClass, true)){
              return replaceReferenceWithShort(reference);
            }
          }
        }
      }

      if (!mySettings.INSERT_INNER_CLASS_IMPORTS) {
        final TreeElement qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
        if (qualifier != null){

          makeShortReference((CompositeElement)qualifier, parentClass, addImports, uncompleteCode);
        }
        return reference;
      }
    }

    PsiFile file = SourceTreeToPsiMap.treeElementToPsi(reference).getContainingFile();
    PsiManager manager = file.getManager();
    PsiResolveHelper helper = manager.getResolveHelper();
    if (addImports){
      if (!myImportHelper.addImport(file, refClass)){
        return reference;
      }
      if (isSafeToShortenReference(reference, refClass, helper)) {
        reference = replaceReferenceWithShort(reference);
      }
    }
    else{
      PsiClass curRefClass = helper.resolveReferencedClass(
        refClass.getName(),
        SourceTreeToPsiMap.treeElementToPsi(reference)
      );
      if (manager.areElementsEquivalent(refClass, curRefClass)){
        reference = replaceReferenceWithShort(reference);
      }
    }
    return reference;
  }

  private static boolean isSafeToShortenReference (CompositeElement reference, PsiClass refClass, PsiResolveHelper helper) {
    PsiClass newRefClass = helper.resolveReferencedClass(
        refClass.getName(),
        SourceTreeToPsiMap.treeElementToPsi(reference)
    );
    return refClass.getManager().areElementsEquivalent(refClass, newRefClass);
  }

  private static CompositeElement replaceReferenceWithShort(CompositeElement reference) {
    ((SourceJavaCodeReference) reference).dequalify();

    return reference;
  }

  private static CompositeElement replaceReferenceWithFQ(CompositeElement reference, PsiClass refClass) {
    ((SourceJavaCodeReference)reference).fullyQualify(refClass);
    return reference;
  }
}

