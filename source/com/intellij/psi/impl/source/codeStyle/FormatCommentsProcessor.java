package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChameleonElement;
import com.intellij.psi.impl.source.tree.CompositeElement;

public class FormatCommentsProcessor implements PreFormatProcessor {
  public TextRange process(final ASTNode element, final TextRange range) {
    final Project project = SourceTreeToPsiMap.treeElementToPsi(element).getProject();
    if (!CodeStyleSettingsManager.getSettings(project).ENABLE_JAVADOC_FORMATTING ||
        element.getPsi().getContainingFile().getLanguage() != StdLanguages.JAVA) {
      return range;
    }

    return formatCommentsInner(project, element, range);
  }

  private static TextRange formatCommentsInner(Project project, ASTNode element, final TextRange range) {
    TextRange result = range;

    PsiElement psi;

    // check for RepositoryTreeElement is optimization
    if (element.getElementType() instanceof JavaStubElementType &&
        (psi = element.getPsi()) instanceof PsiDocCommentOwner) {
      final TextRange elementRange = element.getTextRange();

      if (range.contains(elementRange)) {
        new CommentFormatter(project).process(element);
        final TextRange newRange = element.getTextRange();
        result = new TextRange(range.getStartOffset(), range.getEndOffset() + newRange.getLength() - elementRange.getLength());
      }

      // optimization, does not seek PsiDocComment inside fields / methods or out of range
      if (psi instanceof PsiField ||
          psi instanceof PsiMethod ||
          range.getEndOffset() < elementRange.getStartOffset()
         ) {
        return result;
      }
    }

    if (element instanceof CompositeElement) {
      ASTNode current = element.getFirstChildNode();

      while (current != null) {
        // we expand the chameleons here for effectiveness
        if (current instanceof ChameleonElement) {
          ASTNode next = current.getTreeNext();
          final ASTNode astNode = ChameleonTransforming.transform((ChameleonElement)current);
          if (astNode == null) current = next;
          else current = astNode;
        }
        result = formatCommentsInner(project, current, result);
        current = current.getTreeNext();
      }
    }
    return result;
  }

}
