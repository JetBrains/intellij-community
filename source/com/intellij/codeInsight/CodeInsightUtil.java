package com.intellij.codeInsight;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 *
 */
public class CodeInsightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.CodeInsightUtil");
  @NonNls private static final String JAVA_PACKAGE_PREFIX = "java.";
  @NonNls private static final String JAVAX_PACKAGE_PREFIX = "javax.";

  public static PsiExpression findExpressionInRange(PsiFile file, int startOffset, int endOffset) {
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
    }
    PsiExpression expression = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, PsiExpression.class);
    if (expression == null || expression.getTextRange().getEndOffset() != endOffset) return null;
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) return null;
    return expression;
  }

  public static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return null;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    while (true) {
      if (parent instanceof PsiStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent instanceof PsiCodeBlock) break;
      if (parent instanceof JspFile) break;
      if (parent instanceof PsiCodeFragment) break;
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }


    while (!element1.getParent().equals(parent)) {
      element1 = element1.getParent();
    }
    if (startOffset != element1.getTextRange().getStartOffset()) return null;

    while (!element2.getParent().equals(parent)) {
      element2 = element2.getParent();
    }
    if (endOffset != element2.getTextRange().getEndOffset()) return null;

    if (parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement
        && element1 == ((PsiCodeBlock)parent).getLBrace()
        && element2 == ((PsiCodeBlock)parent).getRBrace()) {
      return new PsiElement[]{parent.getParent()};
    }

/*
    if(parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement) {
      return new PsiElement[]{parent.getParent()};
    }
*/

    PsiElement[] children = parent.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag && !(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : array) {
      if (!(element instanceof PsiStatement
            || element instanceof PsiWhiteSpace
            || element instanceof PsiComment)) {
        return null;
      }
    }

    return array.toArray(new PsiElement[array.size()]);
  }

  public static PsiElement[] getElementsInRange(PsiElement root, final int startOffset, final int endOffset) {
    final List<PsiElement> list = new ArrayList<PsiElement>();

    final ASTNode leafElementAt1 = root.getNode().findLeafElementAt(startOffset);
    if(leafElementAt1 == null) return PsiElement.EMPTY_ARRAY;
    ASTNode leafElementAt2 = root.getNode().findLeafElementAt(endOffset);
    if (leafElementAt2 == null && endOffset == root.getTextLength()) leafElementAt2 = root.getNode().findLeafElementAt(endOffset - 1);
    if(leafElementAt2 == null) return PsiElement.EMPTY_ARRAY;
    TreeElement commonParent = (TreeElement)TreeUtil.findCommonParent(leafElementAt1, leafElementAt2);
    LOG.assertTrue(commonParent != null);
    LOG.assertTrue(commonParent.getTextRange() != null);

    while(commonParent.getTreeParent() != null &&
          commonParent.getTextRange().equals(commonParent.getTreeParent().getTextRange())) {
      commonParent = commonParent.getTreeParent();
    }

    final int currentOffset = commonParent.getTextRange().getStartOffset();
    final TreeElementVisitor visitor = new TreeElementVisitor() {
      int offset = currentOffset;
      public void visitLeaf(LeafElement leaf) {
        offset += leaf.getTextLength();
      }
      public void visitComposite(CompositeElement composite) {
        ChameleonTransforming.transformChildren(composite);
        TreeElement child = (TreeElement)composite.getFirstChildNode();
        for (; child != null; child = child.getTreeNext()) {
          int start = offset;
          if (offset > endOffset) break;
          child.acceptTree(this);
          if (startOffset <= start && offset <= endOffset) {
            list.add(child.getPsi());
          }
        }
      }
    };
    commonParent.acceptTree(visitor);
    list.add(commonParent.getPsi());
    return list.toArray(new PsiElement[list.size()]);
  }

  public static void sortIdenticalShortNameClasses(PsiClass[] classes) {
    if (classes.length <= 1) return;

    final StatisticsManager statisticsManager = StatisticsManager.getInstance();
    Comparator<PsiClass> comparator = new Comparator<PsiClass>() {
      public int compare(PsiClass aClass, PsiClass bClass) {
        int count1 = statisticsManager.getMemberUseCount(null, aClass, null);
        int count2 = statisticsManager.getMemberUseCount(null, bClass, null);
        if (count1 != count2) return count2 - count1;
        boolean inProject1 = aClass.getManager().isInProject(aClass);
        boolean inProject2 = bClass.getManager().isInProject(aClass);
        if (inProject1 != inProject2) return inProject1 ? -1 : 1;
        String qName1 = aClass.getQualifiedName();
        boolean isJdk1 = qName1 != null && (qName1.startsWith(JAVA_PACKAGE_PREFIX) || qName1.startsWith(JAVAX_PACKAGE_PREFIX));
        String qName2 = bClass.getQualifiedName();
        boolean isJdk2 = qName2 != null && (qName2.startsWith(JAVA_PACKAGE_PREFIX) || qName2.startsWith(JAVAX_PACKAGE_PREFIX));
        if (isJdk1 != isJdk2) return isJdk1 ? -1 : 1;
        return 0;
      }
    };
    Arrays.sort(classes, comparator);
  }

  public static Indent getMinLineIndent(Project project, Document document, int line1, int line2, FileType fileType) {
    CharSequence chars = document.getCharsSequence();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    Indent minIndent = null;
    for (int line = line1; line <= line2; line++) {
      int lineStart = document.getLineStartOffset(line);
      int textStart = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (textStart >= document.getTextLength()) {
        textStart = document.getTextLength();
      }
      else {
        char c = chars.charAt(textStart);
        if (c == '\n' || c == '\r') continue; // empty line
      }
      String space = chars.subSequence(lineStart, textStart).toString();
      Indent indent = codeStyleManager.getIndent(space, fileType);
      minIndent = minIndent != null ? indent.min(minIndent) : indent;
    }
    if (minIndent == null && line1 == line2 && line1 < document.getLineCount() - 1) {
      return getMinLineIndent(project, document, line1 + 1, line1 + 1, fileType);
    }
    //if (minIndent == Integer.MAX_VALUE){
    //  minIndent = 0;
    //}
    return minIndent;
  }

  public static String getDefaultValueOfType(PsiType type) {
    if (type instanceof PsiArrayType) {
      int count = type.getArrayDimensions() - 1;
      PsiType componentType = type.getDeepComponentType();

      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        if (classType.resolve() instanceof PsiTypeParameter) {
          return PsiKeyword.NULL;
        }
      }

      StringBuffer buffer = new StringBuffer();
      buffer.append(PsiKeyword.NEW);
      buffer.append(" ");
      buffer.append(componentType.getCanonicalText());
      buffer.append("[0]");
      for (int i = 0; i < count; i++) {
        buffer.append("[]");
      }
      return buffer.toString();
    }
    else if (type instanceof PsiPrimitiveType) {
      if (PsiType.BOOLEAN == type) {
        return PsiKeyword.FALSE;
      }
      else {
        return "0";
      }
    }
    else {
      return PsiKeyword.NULL;
    }
  }

  public static PsiExpression[] findExpressionOccurrences(PsiElement scope, PsiExpression expr) {
    List<PsiExpression> array = new ArrayList<PsiExpression>();
    addExpressionOccurrences(RefactoringUtil.unparenthesizeExpression(expr), array, scope);
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addExpressionOccurrences(PsiExpression expr, List<PsiExpression> array, PsiElement scope) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        if (areExpressionsEquivalent(RefactoringUtil.unparenthesizeExpression((PsiExpression)child), expr)) {
          array.add((PsiExpression)child);
          continue;
        }
      }
      addExpressionOccurrences(expr, array, child);
    }
  }

  public static PsiExpression[] findReferenceExpressions(PsiElement scope, PsiElement referee) {
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    addReferenceExpressions(array, scope, referee);
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addReferenceExpressions(ArrayList<PsiElement> array, PsiElement scope, PsiElement referee) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiReferenceExpression) {
        PsiElement ref = ((PsiReferenceExpression)child).resolve();
        if (ref != null && areElementsEquivalent(ref, referee)) {
          array.add(child);
        }
      }
      addReferenceExpressions(array, child, referee);
    }
  }

  public static boolean areExpressionsEquivalent(PsiExpression expr1, PsiExpression expr2) {
    if (!areElementsEquivalent(expr1, expr2)) return false;
    PsiType type1 = expr1.getType();
    PsiType type2 = expr2.getType();
    return Comparing.equal(type1, type2);
  }

  public static boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    if (!element1.getClass().equals(element2.getClass())) return false; // Q : is it correct to check implementation classes?

    PsiElement[] children1 = getFilteredChildren(element1);
    PsiElement[] children2 = getFilteredChildren(element2);
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!areElementsEquivalent(child1, child2)) return false;
    }

    if (children1.length == 0) {
      if (!element1.textMatches(element2)) return false;
    }

    PsiReference ref1 = element1.getReference();
    if (ref1 != null) {
      PsiReference ref2 = element2.getReference();
      if (ref2 == null) return false;
      if (!Comparing.equal(ref1.resolve(), ref2.resolve())) return false;
    }
    return true;
  }

  private static PsiElement[] getFilteredChildren(PsiElement element1) {
    PsiElement[] children1 = element1.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    for (PsiElement child : children1) {
      if (!(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
    }
    return array.toArray(new PsiElement[array.size()]);
  }

  public static boolean preparePsiElementForWrite(final PsiElement element) {
    PsiFile file = element == null ? null : element.getContainingFile();
    return prepareFileForWrite(file);
  }

  public static boolean prepareFileForWrite(final PsiFile file) {
    if (file == null) return false;

    if (!file.isWritable()) {
      final Project project = file.getProject();

      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(
        new OpenFileDescriptor(project, file.getVirtualFile()), true);

      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {

            if (editor != null && editor.getComponent().isDisplayable()) {
              HintManager.getInstance().showErrorHint(
                editor,
                CodeInsightBundle.message("error.hint.file.is.readonly", file.getVirtualFile().getPresentableUrl()));
            }
          }
        });

        return false;
      }
    }

    return true;
  }

  public static Editor positionCursor(final Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static void findChildRangeDuplicates(PsiElement first, PsiElement last,
                                              List<Pair<PsiElement, PsiElement>> result,
                                              PsiElement scope) {
    LOG.assertTrue(first.getParent() == last.getParent());
    LOG.assertTrue(!(first instanceof PsiWhiteSpace) && !(last instanceof PsiWhiteSpace));
    addRangeDuplicates(scope, first, last, result);
  }

  private static void addRangeDuplicates(final PsiElement scope,
                                         final PsiElement first,
                                         final PsiElement last,
                                         final List<Pair<PsiElement, PsiElement>> result) {
    final PsiElement[] children = getFilteredChildren(scope);
    NextChild:
    for (int i = 0; i < children.length;) {
      PsiElement child = children[i];
      if (child != first) {
        int j = i;
        PsiElement next = first;
        do {
          if (!areElementsEquivalent(children[j], next)) break;
          j++;
          if (next == last) {
            result.add(new Pair<PsiElement, PsiElement>(child, children[j - 1]));
            i = j + 1;
            continue NextChild;
          }
          next = PsiTreeUtil.skipSiblingsForward(next, new Class[]{PsiWhiteSpace.class});
        }
        while (true);

        if (i == j) {
          addRangeDuplicates(child, first, last, result);
        }
      }

      i++;
    }
  }
}

