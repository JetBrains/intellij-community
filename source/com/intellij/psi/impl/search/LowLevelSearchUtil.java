package com.intellij.psi.impl.search;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChameleonElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.StringSearcher;

import java.util.Set;

public class LowLevelSearchUtil {
  public static boolean processElementsContainingWordInElement(PsiElementProcessorEx processor,
                                                               PsiElement scope,
                                                               StringSearcher searcher,
                                                               TokenSet elementTypes,
                                                               ProgressIndicator progress) {
    return processElementsContainingWordInElement(processor, SourceTreeToPsiMap.psiElementToTree(scope), searcher, elementTypes, progress);
  }


  private static boolean processElementsContainingWordInElement(PsiElementProcessorEx processor,
                                                                TreeElement scope,
                                                                StringSearcher searcher,
                                                                TokenSet elementTypes,
                                                                ProgressIndicator progress) {
    ProgressManager.getInstance().checkCanceled();

    if (elementTypes.isInSet(scope.getElementType())) {
      int startOffset;
      int endOffset;
      if (scope instanceof LeafElement) {
        LeafElement leaf = (LeafElement)scope;
        startOffset = 0;
        endOffset = leaf.getTextLength();
        do {
          int i = leaf.searchWord(startOffset, searcher);
          if (i >= 0) {
            if (!processor.execute(SourceTreeToPsiMap.treeElementToPsi(scope), i)) return false;
            startOffset = i + 1;
          }
          else {
            return true;
          }
        }
        while (startOffset < endOffset);
        endOffset = startOffset + leaf.getTextLength();
      }
      else {
        char[] buffer = scope.textToCharArray();

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
        startOffset = 0;
        endOffset = buffer.length;

        final int originalStartOffset = startOffset;
        do {
          int i = searchWord(buffer, startOffset, endOffset, searcher);
          if (i >= 0) {
            if (!processor.execute(SourceTreeToPsiMap.treeElementToPsi(scope), i - originalStartOffset)) return false;
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
      return processChildren(scope, searcher, processor, elementTypes, progress);
    }

    return true;
  }

  private static boolean processChildren(TreeElement scope,
                                         StringSearcher searcher,
                                         PsiElementProcessorEx processor,
                                         TokenSet elementTypes, ProgressIndicator progress) {
    synchronized (PsiLock.LOCK) {
      TreeElement child = ((CompositeElement)scope).firstChild;
      while (child != null) {
        if (child instanceof ChameleonElement) {
          LeafElement leaf = (LeafElement)child;
          if (leaf.searchWord(0, searcher) >= 0) {
            TreeElement next = child.getTreeNext();
            child = ChameleonTransforming.transform((ChameleonElement)child);
            if (child == null) {
              child = next;
            }
            continue;
          }
        }
        child = child.getTreeNext();
      }
    }

    TreeElement child = null;
    while (true) {
      synchronized (PsiLock.LOCK) {
        child = child != null ? child.getTreeNext() : ((CompositeElement)scope).firstChild;
        while (child instanceof ChameleonElement) {
          child = child.getTreeNext();
        }
        if (child == null) break;
      }

      if (!processElementsContainingWordInElement(processor, child, searcher, elementTypes, progress)) return false;
    }
    return true;
  }

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

  public static boolean processIdentifiersBySet(
    PsiElementProcessor processor,
    TreeElement scope,
    TokenSet elementTypes,
    Set namesSet,
    ProgressIndicator progress
    ) {
    ProgressManager.getInstance().checkCanceled();

    if (elementTypes.isInSet(scope.getElementType())) {
      String text = scope.getText(); //TODO: optimization to not fetch text?
      if (namesSet.contains(text)) {
        if (!processor.execute(SourceTreeToPsiMap.treeElementToPsi(scope))) return false;
      }
    }

    if (scope instanceof CompositeElement) {
      CompositeElement _scope = (CompositeElement)scope;
      ChameleonTransforming.transformChildren(_scope);

      for (TreeElement child = _scope.firstChild; child != null; child = child.getTreeNext()) {
        if (!processIdentifiersBySet(processor, child, elementTypes, namesSet, progress)) return false;
      }
    }

    return true;
  }
}
