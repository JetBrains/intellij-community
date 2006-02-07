package com.intellij.psi.impl.search;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.util.text.StringSearcher;

public class LowLevelSearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.LowLevelSearchUtil");
  private LowLevelSearchUtil() {}

  public static boolean processElementsContainingWordInElement(TextOccurenceProcessor processor,
                                                               PsiElement scope,
                                                               StringSearcher searcher,
                                                               ProgressIndicator progress,
                                                               final short searchContext) {
    return processElementsContainingWordInElement(processor, SourceTreeToPsiMap.psiElementToTree(scope), searcher, progress, searchContext);
  }


  private static boolean processElementsContainingWordInElement(TextOccurenceProcessor processor,
                                                                ASTNode scope,
                                                                StringSearcher searcher,
                                                                ProgressIndicator progress,
                                                                final short searchContext) {
    ProgressManager.getInstance().checkCanceled();
    char[] buffer = ((CompositeElement)scope).textToCharArray();
    int startOffset = 0;
    int endOffset = buffer.length;
    final int patternLength = searcher.getPatternLength();

    final int scopeStartOffset = scope.getStartOffset();
    do {
      int i = searchWord(buffer, startOffset, endOffset, searcher);
      if (i >= 0) {
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
        while(run != null) {
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

  private static boolean processChildren(ASTNode scope,
                                         StringSearcher searcher,
                                         TextOccurenceProcessor processor,
                                         ProgressIndicator progress,
                                         final short searchContext) {
    synchronized (PsiLock.LOCK) {
      ASTNode child = scope.getFirstChildNode();
      while (child != null) {
        if (child instanceof LeafElement && ((LeafElement)child).isChameleon()) {
          LeafElement leaf = (LeafElement)child;
          if (leaf.searchWord(0, searcher) >= 0) {
            ASTNode next = child.getTreeNext();
            child = ChameleonTransforming.transform((LeafElement)child);
            if (child == null) {
              child = next;
            }
          continue;
          }
        }
        child = child.getTreeNext();
      }
    }

    ASTNode child = null;
    while (true) {
      synchronized (PsiLock.LOCK) {
        child = child != null ? child.getTreeNext() : scope.getFirstChildNode();
        while (child instanceof LeafElement && ((LeafElement)child).isChameleon()) {
          child = child.getTreeNext();
        }
        if (child == null) break;
      }

      if (!processElementsContainingWordInElement(processor, child, searcher, progress, searchContext)) {
        return false;
      }
    }
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
