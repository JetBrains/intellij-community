package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ReferenceAdjuster implements Constants {
  private final boolean myUseFqClassnamesInJavadoc;
  private final boolean myUseFqClassNames;

  public ReferenceAdjuster(boolean useFqInJavadoc, boolean useFqInCode) {
    myUseFqClassnamesInJavadoc = useFqInJavadoc;
    myUseFqClassNames = useFqInCode;
  }

  public ReferenceAdjuster(Project project) {
    this(CodeStyleSettingsManager.getSettings(project));
  }

  public ReferenceAdjuster(CodeStyleSettings settings) {
    this(settings.USE_FQ_CLASS_NAMES_IN_JAVADOC, settings.USE_FQ_CLASS_NAMES);
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
      return;
    }

    if (parent instanceof CompositeElement) {
      addReferencesInRangeForComposite(array, (CompositeElement)parent, startOffset, endOffset);
    }
  }

  private static void addReferencesInRangeForComposite(final ArrayList<ASTNode> array, final CompositeElement parent,
                                                       final int startOffset,
                                                       final int endOffset) {
    ChameleonTransforming.transformChildren(parent);

    int offset = 0;
    for (TreeElement child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      int length = child.getTextLength();

      if (startOffset <= offset + length && offset <= endOffset) {
        final IElementType type = child.getElementType();

        if (type == ElementType.JAVA_CODE_REFERENCE || type == ElementType.REFERENCE_EXPRESSION) {
          array.add(child);
        } else if (child instanceof CompositeElement) {
          addReferencesInRangeForComposite(array, (CompositeElement)child, startOffset - offset, endOffset - offset);
        }
      }
      offset += length;
    }
  }

  private static ASTNode makeShortReference(@NotNull CompositeElement reference, @NotNull PsiClass refClass, boolean addImports) {
    @NotNull final PsiJavaCodeReferenceElement psiReference = (PsiJavaCodeReferenceElement)reference.getPsi();
    final PsiQualifiedReference reference1 = getClassReferenceToShorten(refClass, addImports, psiReference);
    if (reference1 != null) replaceReferenceWithShort(reference1);
    return reference;
  }

  @Nullable
  public static PsiQualifiedReference getClassReferenceToShorten(@NotNull final PsiClass refClass,
                                                                 final boolean addImports,
                                                                 @NotNull final PsiQualifiedReference reference) {
    PsiClass parentClass = refClass.getContainingClass();
    if (parentClass != null) {
      PsiManager manager = parentClass.getManager();
      final PsiResolveHelper resolveHelper = manager.getResolveHelper();
      if (resolveHelper.isAccessible(refClass, reference, null) &&
          isSafeToShortenReference(reference.getReferenceName(), reference, refClass)) {
        return reference;
      }

      if (!CodeStyleSettingsManager.getSettings(reference.getProject()).INSERT_INNER_CLASS_IMPORTS) {
        final PsiElement qualifier = reference.getQualifier();
        if (qualifier instanceof PsiQualifiedReference) {
          return getClassReferenceToShorten(parentClass, addImports, (PsiQualifiedReference)qualifier);
        }
        return null;
      }
    }

    if (addImports && !((PsiImportHolder) reference.getContainingFile()).importClass(refClass)) return null;
    if (!isSafeToShortenReference(reference, refClass)) return null;
    return reference;
  }

  private static boolean isSafeToShortenReference(@NotNull PsiElement psiReference, @NotNull PsiClass refClass) {
    return isSafeToShortenReference(refClass.getName(), psiReference, refClass);
  }

  private static boolean isSafeToShortenReference(final String referenceText, final PsiElement psiReference, final PsiClass refClass) {
    final PsiManager manager = refClass.getManager();
    return manager.areElementsEquivalent(refClass, manager.getResolveHelper().resolveReferencedClass(referenceText, psiReference));
  }

  @NotNull
  private static ASTNode replaceReferenceWithShort(PsiQualifiedReference reference) {
    final ASTNode node = reference.getNode();
    assert node != null;
    SourceUtil.dequalifyImpl((CompositeElement)node);
    return node;
  }

  private static ASTNode replaceReferenceWithFQ(ASTNode reference, PsiClass refClass) {
      ((SourceJavaCodeReference)reference).fullyQualify(refClass);
    return reference;
  }
}

