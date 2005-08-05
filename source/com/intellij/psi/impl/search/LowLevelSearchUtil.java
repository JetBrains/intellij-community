package com.intellij.psi.impl.search;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.StringSearcher;

import java.util.Set;

public class LowLevelSearchUtil {
  public static boolean processElementsContainingWordInElement(PsiElementProcessorEx processor,
                                                               PsiElement scope,
                                                               StringSearcher searcher,
                                                               ProgressIndicator progress,
                                                               final short searchContext) {
    return processElementsContainingWordInElement(processor, SourceTreeToPsiMap.psiElementToTree(scope), searcher, progress, searchContext);
  }


  private static boolean processElementsContainingWordInElement(PsiElementProcessorEx processor,
                                                                ASTNode scope,
                                                                StringSearcher searcher,
                                                                ProgressIndicator progress,
                                                                final short searchContext) {
    ProgressManager.getInstance().checkCanceled();
    final PsiElement scopePsi = SourceTreeToPsiMap.treeElementToPsi(scope);
    if (scopePsi instanceof PsiWhiteSpace) {
      // Optimization. Taking language from whitespace may expand a chameleon next to this whitespace
      // As we know for sure whitespaces may not have words in them this optimization is safe.
      return true;
    }

    final Language lang = scopePsi.getLanguage();
    //TODO[ven]: lang is null for PsiPlainText for example. Should be reviewed.
    if (lang == null || lang.getFindUsagesProvider().mayHaveReferences(scope.getElementType(), searchContext)) {
      int startOffset;
      int endOffset;
      if (scope instanceof LeafElement) {
        LeafElement leaf = (LeafElement)scope;
        startOffset = 0;
        endOffset = leaf.getTextLength();
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
        endOffset = startOffset + leaf.getTextLength();
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
        startOffset = 0;
        endOffset = buffer.length;

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
    }

    return true;
  }

  private static boolean processChildren(ASTNode scope,
                                         StringSearcher searcher,
                                         PsiElementProcessorEx processor,
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
    ASTNode scope,
    TokenSet elementTypes,
    Set namesSet,
    ProgressIndicator progress) {
    ProgressManager.getInstance().checkCanceled();

    if (elementTypes.isInSet(scope.getElementType())) {
      String text = scope.getText(); //TODO: optimization to not fetch text?
      if (namesSet.contains(text)) {
        if (!processor.execute(SourceTreeToPsiMap.treeElementToPsi(scope))) return false;
      }
    }

    if (scope instanceof CompositeElement) {
      ASTNode _scope = scope;
      ChameleonTransforming.transformChildren(_scope);

      for (ASTNode child = _scope.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (!processIdentifiersBySet(processor, child, elementTypes, namesSet, progress)) return false;
      }
    }

    return true;
  }
}
