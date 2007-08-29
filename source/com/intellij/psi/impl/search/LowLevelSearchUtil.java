package com.intellij.psi.impl.search;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.StringSearcher;

import java.util.List;

public class LowLevelSearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.LowLevelSearchUtil");

  private LowLevelSearchUtil() {
  }

  // TRUE/FALSE -> injected psi has been discovered and processor returned true/false;
  // null -> there were nothing injected found
  private static Boolean processInjectedFile(PsiElement element, final TextOccurenceProcessor processor, final StringSearcher searcher) {
    if (!(element instanceof PsiLanguageInjectionHost)) return null;
    List<Pair<PsiElement,TextRange>> list = ((PsiLanguageInjectionHost)element).getInjectedPsi();
    if (list == null) return null;
    for (Pair<PsiElement, TextRange> pair : list) {
      final PsiElement injected = pair.getFirst();
      if (!processElementsContainingWordInElement(processor, injected, searcher, false)) return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  private static boolean processTreeUp(final TextOccurenceProcessor processor,
                                       final PsiElement scope,
                                       final StringSearcher searcher,
                                       final int offset,
                                       final boolean ignoreInjectedPsi) {
    final int scopeStartOffset = scope.getTextRange().getStartOffset();
    final int patternLength = searcher.getPatternLength();
    PsiElement leafElement = null;
    TreeElement leafNode = null;
    ASTNode scopeNode = scope.getNode();
    boolean useTree = scopeNode != null;

    int start;
    if (useTree) {
      leafNode = (LeafElement)scopeNode.findLeafElementAt(offset);
      if (leafNode == null) return true;
      start = offset - leafNode.getStartOffset() + scopeStartOffset;
    }
    else {
      if (scope instanceof PsiFile) {
        leafElement = ((PsiFile)scope).getViewProvider().findElementAt(offset, scope.getLanguage());
      }
      else {
        leafElement = scope.findElementAt(offset);
      }
      if (leafElement == null) return true;
      start = offset - leafElement.getTextRange().getStartOffset() + scopeStartOffset;
    }
    if (start < 0) {
      LOG.error("offset=" + offset + " scopeStartOffset=" + scopeStartOffset + " leafElement=" + leafElement + " " +
                                  " scope=" + scope.toString());
    }
    boolean contains = false;
    PsiElement prev = null;
    TreeElement prevNode = null;
    PsiElement run = null;
    while (run != scope) {
      if (useTree) {
        start += prevNode == null ? 0 : prevNode.getStartOffsetInParent();
        prevNode = leafNode;
        run = leafNode.getPsi();
      }
      else {
        start += prev == null ? 0 : prev.getStartOffsetInParent();
        prev = run;
        run = leafElement;
      }
      contains |= run.getTextLength() - start >= patternLength;  //do not compute if already contains
      if (contains) {
        if (!ignoreInjectedPsi) {
          Boolean result = processInjectedFile(run, processor, searcher);
          if (result != null) return result.booleanValue();
        }
        if (!processor.execute(run, start)) return false;
      }
      if (useTree) {
        leafNode = leafNode.getTreeParent();
        if (leafNode == null) break;
      }
      else {
        leafElement = leafElement.getParent();
        if (leafElement == null) break;
      }
    }
    assert run == scope: "Malbuilt PSI: scopeNode="+scope+"; leafNode="+run+"; isAncestor="+ PsiTreeUtil.isAncestor(scope, run, false);

    return true;
  }
  //@RequiresReadAction
  public static boolean processElementsContainingWordInElement(final TextOccurenceProcessor processor,
                                                               final PsiElement scope,
                                                               final StringSearcher searcher,
                                                               final boolean ignoreInjectedPsi) {
    ProgressManager.getInstance().checkCanceled();
    final CharSequence buffer = scope instanceof PsiFile ? ((PsiFile)scope).getViewProvider().getContents():new CharArrayCharSequence(scope.textToCharArray());
    int startOffset = 0;
    int endOffset = buffer.length();

    do {
      startOffset  = searchWord(buffer, startOffset, endOffset, searcher);
      if (startOffset < 0) {
        return true;
      }
      if (CachesBasedRefSearcher.DEBUG) {
        System.out.println(">>>>>>>>>>>>>>>>>>> found word:" + startOffset + " in " + scope + "," + scope.getContainingFile().getName());
      }
      if (!processTreeUp(processor, scope, searcher, startOffset, ignoreInjectedPsi)) return false;

      startOffset++;
    }
    while (startOffset < endOffset);

    return true;
  }

  public static int searchWord(CharSequence text, int startOffset, int endOffset, StringSearcher searcher) {
    for (int index = startOffset; index < endOffset; index++) {
      //noinspection AssignmentToForLoopParameter
      index = searcher.scan(text, index, endOffset);
      if (index < 0) return -1;
      if (!searcher.isJavaIdentifier()) {
        return index;
      }
      
      if (index > startOffset) {
        char c = text.charAt(index - 1);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          if (index < 2 || text.charAt(index - 2) != '\\') { //escape sequence
            continue;
          }
        }
      }

      String pattern = searcher.getPattern();
      if (index + pattern.length() < endOffset) {
        char c = text.charAt(index + pattern.length());
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }
      return index;
    }
    return -1;
  }
}
