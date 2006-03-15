package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
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
                                                               StringSearcher searcher,
                                                               ProgressIndicator progress,
                                                               final short searchContext) {
    ProgressManager.getInstance().checkCanceled();
    char[] buffer = scope.textToCharArray();
    int startOffset = 0;
    int endOffset = buffer.length;
    final int patternLength = searcher.getPatternLength();

    final int scopeStartOffset = scope.getTextRange().getStartOffset();
    do {
      int i = searchWord(buffer, startOffset, endOffset, searcher);
      if (i >= 0) {

        if (scope instanceof TreeElement) {
          LeafElement leafNode = ((TreeElement)scope).findLeafElementAt(i);
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
            if (run == scope) break;
            run = run.getTreeParent();
          }
          assert run == scope;
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

    /*scope.findLeafElementAt()
    final PsiElement scopePsi = SourceTreeToPsiMap.treeElementToPsi(scope);
    if (scopePsi instanceof PsiWhiteSpace) {
      // Optimization. Taking language from whitespace may expand a chameleon next to this whitespace
      // As we know for sure whitespaces may not have words in them this optimization is safe.
      return true;
    }

    final Language lang = scopePsi.getLanguage();
    if (lang.getFindUsagesProvider().mayHaveReferences(scope.getElementType(), searchContext)) {
      if (scope instanceof LeafElement) {
        LeafElement leaf = (LeafElement)scope;
        int startOffset = 0;
        int endOffset = leaf.getTextLength();
        do {
          int i = leaf.searchWord(startOffset, searcher);
          if (i >= 0) {
            if (!processor.execute(scopePsi, i)) return false;
            startOffset = i + 1;
          }
          else {
            return true;
          }
        }
        while (startOffset < endOffset);
      }
      else {
        char[] buffer = ((CompositeElement)scope).textToCharArray();

        // This is hack. Need to be fixed and optimized. current code's extremely slow
        // LeafElement leaf = SourceUtil.findLeafToFetchCharArrayRange(scope);
        //if (leaf != null) {
        //  buffer = leaf.buffer;
        //  startOffset = leaf.offset;
        //  endOffset = leaf.offset + scope.getTextLength();
        //}
        //else {
        //  buffer = scope.textToCharArray();
        //  startOffset = 0;
        //  endOffset = buffer.length;
        //}
        int startOffset = 0;
        int endOffset = buffer.length;

        final int originalStartOffset = startOffset;
        do {
          int i = searchWord(buffer, startOffset, endOffset, searcher);
          if (i >= 0) {
            if (!processor.execute(scopePsi, i - originalStartOffset)) return false;
            startOffset = i + 1;
          }
          else {
            return true;
          }
        }
        while (startOffset < endOffset);
      }
    }

    if (scope instanceof CompositeElement) {
      return processChildren(scope, searcher, processor, progress, searchContext);
    }*/

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
