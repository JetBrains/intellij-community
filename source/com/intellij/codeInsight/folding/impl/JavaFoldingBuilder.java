package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.xml.XmlFoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaFoldingBuilder implements FoldingBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.JavaFoldingBuilder");

  public FoldingDescriptor[] buildFoldRegions(final ASTNode node, final Document document) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (!(element instanceof PsiJavaFile)) {
      return FoldingDescriptor.EMPTY;
    }
    PsiJavaFile file = (PsiJavaFile) element;

    List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    PsiImportList importList = file.getImportList();
    if (importList != null) {
      PsiImportStatementBase[] statements = importList.getAllImportStatements();
      if (statements.length > 1) {
        final TextRange rangeToFold = getRangeToFold(importList);
        if (rangeToFold != null) {
          result.add(new FoldingDescriptor(importList.getNode(), rangeToFold));
        }
      }
    }

    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      ProgressManager.getInstance().checkCanceled();
      addElementsToFold(result, aClass, document, true);
    }

    TextRange range = getFileHeader(file);
    if (range != null && document.getLineNumber(range.getEndOffset()) > document.getLineNumber(range.getStartOffset())) {
      result.add(new FoldingDescriptor(file.getNode(), range));
    }

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private void addElementsToFold(List<FoldingDescriptor> list, PsiClass aClass, Document document, boolean foldJavaDocs) {
    if (!(aClass.getParent() instanceof PsiJavaFile) || ((PsiJavaFile)aClass.getParent()).getClasses().length > 1) {
      addToFold(list, aClass, document, true);
    }

    PsiDocComment docComment;
    if (foldJavaDocs) {
      docComment = aClass.getDocComment();
      if (docComment != null) {
        addToFold(list, docComment, document, true);
      }
    }
    addAnnotationsToFold(aClass.getModifierList(), list, document);

    PsiElement[] children = aClass.getChildren();
    for (PsiElement child : children) {
      ProgressManager.getInstance().checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;
        addToFold(list, method, document, true);
        addAnnotationsToFold(method.getModifierList(), list, document);

        if (foldJavaDocs) {
          docComment = method.getDocComment();
          if (docComment != null) {
            addToFold(list, docComment, document, true);
          }
        }

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          findClasses(body, list, document);
        }
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;
        if (foldJavaDocs) {
          docComment = field.getDocComment();
          if (docComment != null) {
            addToFold(list, docComment, document, true);
          }
        }
        addAnnotationsToFold(field.getModifierList(), list, document);
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          findClasses(initializer, list, document);
        } else if (field instanceof PsiEnumConstant) {
          findClasses(field, list, document);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer)child;
        addToFold(list, initializer, document, true);
        findClasses(initializer, list, document);
      }
      else if (child instanceof PsiClass) {
        addElementsToFold(list, (PsiClass)child, document, true);
      }
    }
  }

  public String getPlaceholderText(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof PsiImportList) {
      return "...";
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiClass) {
      return "{...}";
    }
    else if (element instanceof PsiDocComment) {
      return "/**...*/";
    }
    else if (element instanceof PsiFile) {
      return "/.../";
    }
    else if (element instanceof PsiAnnotation) {
      return "@{...}";
    }
    return "...";
  }

  public boolean isCollapsedByDefault(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    CodeFoldingSettings settings = CodeFoldingSettings.getInstance();
    if (element instanceof PsiImportList) {
      return settings.isCollapseImports();
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer) {
      if (!settings.isCollapseAccessors() && !settings.isCollapseMethods()) {
        return false;
      }
      if (element instanceof PsiMethod && isSimplePropertyAccessor((PsiMethod)element)) {
        return settings.isCollapseAccessors();
      }
      return settings.isCollapseMethods();
    }
    else if (element instanceof PsiAnonymousClass) {
      return settings.isCollapseAnonymousClasses();
    }
    else if (element instanceof PsiClass) {
      return !(element.getParent() instanceof PsiFile) && settings.isCollapseInnerClasses();
    }
    else if (element instanceof PsiDocComment) {
      return settings.isCollapseJavadocs();
    }
    else if (element instanceof PsiJavaFile) {
      return settings.isCollapseFileHeader();
    }
    else if (element instanceof PsiAnnotation) {
      return settings.isCollapseAnnotations();
    }
    else {
      LOG.error("Unknown element:" + element);
      return false;
    }
  }

  private static boolean isSimplePropertyAccessor(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) return false;
    PsiStatement statement = statements[0];
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      if (statement instanceof PsiReturnStatement) {
        PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
        if (returnValue instanceof PsiReferenceExpression) {
          return ((PsiReferenceExpression)returnValue).resolve() instanceof PsiField;
        }
      }
    }
    else if (PropertyUtil.isSimplePropertySetter(method)) {
      if (statement instanceof PsiExpressionStatement) {
        PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
        if (expr instanceof PsiAssignmentExpression) {
          PsiExpression lhs = ((PsiAssignmentExpression)expr).getLExpression();
          PsiExpression rhs = ((PsiAssignmentExpression)expr).getRExpression();
          if (lhs instanceof PsiReferenceExpression && rhs instanceof PsiReferenceExpression) {
            return ((PsiReferenceExpression)lhs).resolve() instanceof PsiField &&
                   ((PsiReferenceExpression)rhs).resolve() instanceof PsiParameter;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  public static TextRange getRangeToFold(PsiElement element) {
    if (element instanceof PsiMethod) {
      if (element instanceof JspHolderMethod) return null;
      PsiCodeBlock body = ((PsiMethod)element).getBody();
      if (body == null) return null;
      return body.getTextRange();
    }
    if (element instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)element).getBody().getTextRange();
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiJavaToken lBrace = aClass.getLBrace();
      if (lBrace == null) return null;
      PsiJavaToken rBrace = aClass.getRBrace();
      if (rBrace == null) return null;
      return new TextRange(lBrace.getTextOffset(), rBrace.getTextOffset() + 1);
    }
    if (element instanceof PsiJavaFile) {
      return getFileHeader((PsiJavaFile)element);
    }
    if (element instanceof PsiImportList) {
      PsiImportList list = (PsiImportList)element;
      PsiImportStatementBase[] statements = list.getAllImportStatements();
      if (statements.length == 0) return null;
      final PsiElement importKeyword = statements[0].getFirstChild();
      if (importKeyword == null) return null;
      int startOffset = importKeyword.getTextRange().getEndOffset() + 1;
      int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    if (element instanceof PsiDocComment) {
      return element.getTextRange();
    }
    if (element instanceof XmlTag) {
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(element.getLanguage());

      if (foldingBuilder instanceof XmlFoldingBuilder) {
        return ((XmlFoldingBuilder)foldingBuilder).getRangeToFold(element);
      }
    }
    else if (element instanceof PsiAnnotation) {
      int startOffset = element.getTextRange().getStartOffset();
      PsiElement last = element;
      while (element instanceof PsiAnnotation) {
        last = element;
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiComment.class);
      }

      return new TextRange(startOffset, last.getTextRange().getEndOffset());
    }


    return null;
  }

  @Nullable
  private static TextRange getFileHeader(PsiJavaFile file) {
    PsiElement first = file.getFirstChild();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    PsiElement element = first;
    while (element instanceof PsiComment) {
      element = element.getNextSibling();
      if (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      else {
        break;
      }
    }
    if (element == null) return null;
    if (element.getPrevSibling() instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (element == null || element.equals(first)) return null;
    return new TextRange(first.getTextOffset(), element.getTextOffset());
  }

  private void addAnnotationsToFold(PsiModifierList modifierList, List<FoldingDescriptor> foldElements, Document document) {
    if (modifierList == null) return;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child instanceof PsiAnnotation) {
        addToFold(foldElements, child, document, false);
        int j;
        for (j = i + 1; j < children.length; j++) {
          PsiElement nextChild = children[j];
          if (nextChild instanceof PsiModifier) break;
        }

        //noinspection AssignmentToForLoopParameter
        i = j;
      }
    }
  }

  private void findClasses(PsiElement scope, final List<FoldingDescriptor> foldElements, final Document document) {
    scope.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitClass(PsiClass aClass) {
        addToFold(foldElements, aClass, document, true);
        addElementsToFold(foldElements, aClass, document, false);
      }
    });
  }

  private boolean addToFold(List<FoldingDescriptor> list, PsiElement elementToFold, Document document, boolean allowOneLiners) {
    LOG.assertTrue(elementToFold.isValid());
    TextRange range = JavaFoldingBuilder.getRangeToFold(elementToFold);
    if (range == null) return false;
    final TextRange fileRange = elementToFold.getContainingFile().getTextRange();
    if (range.equals(fileRange)) return false;

    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= fileRange.getEndOffset());
    // PSI element text ranges may be invalid because of reparse exception (see, for example, IDEA-10617)
    if (range.getStartOffset() < 0 || range.getEndOffset() > fileRange.getEndOffset()) {
      return false;
    }
    if (!allowOneLiners) {
      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      if (startLine < endLine) {
        list.add(new FoldingDescriptor(elementToFold.getNode(), range));
        return true;
      }
      else {
        return false;
      }
    }
    else {
      if (range.getEndOffset() - range.getStartOffset() > getPlaceholderText(elementToFold.getNode()).length()) {
        list.add(new FoldingDescriptor(elementToFold.getNode(), range));
        return true;
      }
      else {
        return false;
      }
    }
  }
}

