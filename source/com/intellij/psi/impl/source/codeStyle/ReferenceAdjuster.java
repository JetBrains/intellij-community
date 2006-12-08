package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;

class ReferenceAdjuster implements Constants {
  private final CodeStyleSettings mySettings;
  private final boolean myUseFqClassnamesInJavadoc;
  private final boolean myUseFqClassNames;

  public ReferenceAdjuster(CodeStyleSettings settings, boolean useFqInJavadoc, boolean useFqInCode) {
    mySettings = settings;
    myUseFqClassnamesInJavadoc = useFqInJavadoc;
    myUseFqClassNames = useFqInCode;
  }

  public ReferenceAdjuster(CodeStyleSettings settings) {
    this(settings, settings.USE_FQ_CLASS_NAMES_IN_JAVADOC, settings.USE_FQ_CLASS_NAMES);
  }

  public TreeElement process(TreeElement element, boolean addImports, boolean uncompleteCode) {
    IElementType elementType = element.getElementType();
    if (elementType == JAVA_CODE_REFERENCE || elementType == REFERENCE_EXPRESSION) {
      if (elementType == JAVA_CODE_REFERENCE || element.getTreeParent().getElementType() == REFERENCE_EXPRESSION || uncompleteCode) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(element);
        final PsiReferenceParameterList parameterList = ref.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeParameters = parameterList.getTypeParameterElements();
          for (PsiTypeElement typeParameter : typeParameters) {
            process((TreeElement)SourceTreeToPsiMap.psiElementToTree(typeParameter), addImports, uncompleteCode);
          }
        }

        boolean rightKind = true;
        if (elementType == JAVA_CODE_REFERENCE) {
          int kind = ((PsiJavaCodeReferenceElementImpl)element).getKind();
          rightKind = kind == PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND ||
            kind == PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND;
        }

        if (rightKind) {
          boolean isInsideDocComment = TreeUtil.findParent(element, JavaDocElementType.DOC_COMMENT) != null;
          boolean isShort = !((SourceJavaCodeReference)element).isQualified();
          if (!makeFQ(isInsideDocComment)) {
            if (isShort) return element; // short name already, no need to change
          }
          PsiElement refElement;
          if (!uncompleteCode) {
            refElement = ref.resolve();
          }
          else {
            PsiResolveHelper helper = element.getManager().getResolveHelper();
            refElement = helper.resolveReferencedClass(
                ((SourceJavaCodeReference)element).getClassNameText(),
              SourceTreeToPsiMap.treeElementToPsi(element)
            );
          }
          if (refElement instanceof PsiClass) {
            if (makeFQ(isInsideDocComment)) {
              String qName = ((PsiClass)refElement).getQualifiedName();
              if (qName == null) return element;
              PsiImportHolder file = (PsiImportHolder) SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile();
              if (file instanceof PsiJavaFile && ImportHelper.isImplicitlyImported(qName, (PsiJavaFile) file)) {
                if (isShort) return element;
                return (TreeElement)makeShortReference((CompositeElement)element, (PsiClass)refElement, addImports);
              }
              if (file instanceof PsiJavaFile) {
                String thisPackageName = ((PsiJavaFile)file).getPackageName();
                if (ImportHelper.hasPackage(qName, thisPackageName)) {
                  if (!isShort) {
                    return (TreeElement)makeShortReference(
                      (CompositeElement)element,
                      (PsiClass)refElement,
                      addImports);
                  }
                }
              }
              return (TreeElement)replaceReferenceWithFQ(element, (PsiClass)refElement);
            }
            else {
              return (TreeElement)makeShortReference((CompositeElement)element, (PsiClass)refElement, addImports);
            }
          }
        }
      }
    }

    if (element instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(element);
      for (TreeElement child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        child = process(child, addImports, uncompleteCode);
      }
    }

    return element;
  }

  private boolean makeFQ(boolean isInsideDocComment) {
    if (isInsideDocComment) {
      return myUseFqClassnamesInJavadoc;
    }
    else {
      return myUseFqClassNames;
    }
  }

  public void processRange(TreeElement element, int startOffset, int endOffset) {
    ArrayList<ASTNode> array = new ArrayList<ASTNode>();
    addReferencesInRange(array, element, startOffset, endOffset);
    for (ASTNode ref : array) {
      if (SourceTreeToPsiMap.treeElementToPsi(ref).isValid()) {
        process((TreeElement)ref, true, true);
      }
    }
  }

  private static void addReferencesInRange(ArrayList<ASTNode> array, TreeElement parent, int startOffset, int endOffset) {
    if (parent.getElementType() == ElementType.JAVA_CODE_REFERENCE || parent.getElementType() == ElementType.REFERENCE_EXPRESSION) {
      array.add(parent);
      return;
    }

    if (parent.getPsi() instanceof PsiFile && PsiUtil.isInJspFile(parent.getPsi())) {
      final JspFile jspFile = (PsiUtil.getJspFile(parent.getPsi()));
      JspClass jspClass = (JspClass) jspFile.getJavaClass();
      addReferencesInRange(array, (TreeElement)jspClass.getNode(), startOffset, endOffset);
      JspHolderMethod holderMethod = jspClass.getHolderMethod();
      addReferencesInRange(array, (TreeElement)holderMethod.getNode(), startOffset, endOffset);
    }

    if (parent instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(parent);
      int offset = 0;
      for (TreeElement child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        int length = child.getTextLength();
        if (startOffset <= offset + length && offset <= endOffset) {
          if (startOffset <= offset && offset + length <= endOffset) {
            array.add(child);
          }
          addReferencesInRange(array, child, startOffset - offset, endOffset - offset);
        }
        offset += length;
      }
    }
  }

  private ASTNode makeShortReference(
      CompositeElement reference,
      PsiClass refClass,
      boolean addImports
  ) {
    PsiClass parentClass = refClass.getContainingClass();
    if (parentClass != null) {
      PsiJavaCodeReferenceElement psiReference = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(reference);
      PsiManager manager = parentClass.getManager();
      final PsiResolveHelper resolveHelper = manager.getResolveHelper();
      if (resolveHelper.isAccessible(refClass, psiReference, null)) {
        final PsiClass resolved = resolveHelper.resolveReferencedClass(psiReference.getReferenceName(), reference.getPsi());
        if (manager.areElementsEquivalent(resolved, refClass)) {
          return replaceReferenceWithShort(reference);
        }
      }

      if (!mySettings.INSERT_INNER_CLASS_IMPORTS) {
        final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
        if (qualifier != null &&
          (qualifier.getElementType() == JAVA_CODE_REFERENCE || qualifier.getElementType() == REFERENCE_EXPRESSION)) {

          makeShortReference((CompositeElement)qualifier, parentClass, addImports);
        }
        return reference;
      }
    }

    PsiImportHolder file = (PsiImportHolder) SourceTreeToPsiMap.treeElementToPsi(reference).getContainingFile();
    PsiManager manager = file.getManager();
    PsiResolveHelper helper = manager.getResolveHelper();
    if (addImports) {
      if (!file.importClass(refClass)) {
        return reference;
      }
      if (isSafeToShortenReference(reference, refClass, helper)) {
        reference = replaceReferenceWithShort(reference);
      }
    }
    else {
      PsiClass curRefClass = helper.resolveReferencedClass(
        refClass.getName(),
        SourceTreeToPsiMap.treeElementToPsi(reference)
      );
      if (manager.areElementsEquivalent(refClass, curRefClass)) {
        reference = replaceReferenceWithShort(reference);
      }
    }
    return reference;
  }

  private static boolean isSafeToShortenReference(ASTNode reference, PsiClass refClass, PsiResolveHelper helper) {
    PsiClass newRefClass = helper.resolveReferencedClass(
      refClass.getName(),
      SourceTreeToPsiMap.treeElementToPsi(reference)
    );
    return refClass.getManager().areElementsEquivalent(refClass, newRefClass);
  }

  private static CompositeElement replaceReferenceWithShort(CompositeElement reference) {
      ((SourceJavaCodeReference)reference).dequalify();

    return reference;
  }

  private static ASTNode replaceReferenceWithFQ(ASTNode reference, PsiClass refClass) {
      ((SourceJavaCodeReference)reference).fullyQualify(refClass);
    return reference;
  }
}

