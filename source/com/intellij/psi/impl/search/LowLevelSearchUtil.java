package com.intellij.psi.impl.search;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.util.text.StringSearcher;

public class LowLevelSearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.LowLevelSearchUtil");

  private LowLevelSearchUtil() {
  }

  public static boolean processElementsContainingWordInElement(TextOccurenceProcessor processor,
                                                               PsiElement scope,
                                                               StringSearcher searcher) {
    ProgressManager.getInstance().checkCanceled();
    char[] buffer = scope.textToCharArray();
    int startOffset = 0;
    int endOffset = buffer.length;
    final int patternLength = searcher.getPatternLength();

    final int scopeStartOffset = scope.getTextRange().getStartOffset();
    do {
      int i = searchWord(buffer, startOffset, endOffset, searcher);
      if (i >= 0) {
        final ASTNode node = scope.getNode();
        if (node != null) {
          LeafElement leafNode = (LeafElement)node.findLeafElementAt(i);
          if (leafNode == null) return true;
          int start = i - leafNode.getStartOffset() + scopeStartOffset;
          LOG.assertTrue(start >= 0);
          boolean contains = leafNode.getTextLength() - start >= patternLength;
          if (contains) {
            if (!processor.execute(leafNode.getPsi(), start)) return false;
          }

          TreeElement prev = leafNode;
          CompositeElement run = leafNode.getTreeParent();
          while (run != null) {
            start += prev.getStartOffsetInParent();
            contains |= run.getTextLength() - start >= patternLength;  //do not compute if already contains
            if (contains) {
              if (!processor.execute(run.getPsi(), start)) return false;
            }
            prev = run;
            if (run == node) break;
            run = run.getTreeParent();
          }
          assert run == node;
        }
        else {
          PsiElement leafElement;
          if(scope instanceof PsiFile)
            leafElement = ((PsiFile)scope).getViewProvider().findElementAt(i, scope.getLanguage());
          else
            leafElement = scope.findElementAt(i);
          if (leafElement == null) return true;
          int start = i - leafElement.getTextRange().getStartOffset() + scopeStartOffset;
          LOG.assertTrue(start >= 0);
          boolean contains = leafElement.getTextLength() - start >= patternLength;
          if (contains) {
            if (!processor.execute(leafElement, start)) return false;
          }

          PsiElement prev = leafElement;
          PsiElement run = leafElement.getParent();
          while (run != null) {
            start += prev.getStartOffsetInParent();
            contains |= run.getTextLength() - start >= patternLength;  //do not compute if already contains
            if (contains) {
              if (!processor.execute(run, start)) return false;
            }
            prev = run;
            if (run == scope) break;
            run = run.getParent();
          }
          assert run == scope;
        }

        startOffset = i + 1;
      }
      else {
        return true;
      }
    }
    while (startOffset < endOffset);

    return true;
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  public static int searchWord(char[] text, int startOffset, int endOffset, StringSearcher searcher) {
    for (int index = startOffset; index < endOffset; index++) {
      index = searcher.scan(text, index, endOffset);
      if (index < 0) return -1;

      if (index > startOffset) {
        char c = text[index - 1];
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }

      String pattern = searcher.getPattern();
      if (index + pattern.length() < endOffset) {
        char c = text[index + pattern.length()];
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }
      return index;
    }
    return -1;
  }
}
