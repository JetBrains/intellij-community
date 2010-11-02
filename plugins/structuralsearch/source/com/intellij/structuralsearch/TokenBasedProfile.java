package com.intellij.structuralsearch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementInfoImpl;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class TokenBasedProfile extends StructuralSearchProfile {
  @NotNull
  @Override
  public CompiledPattern createCompiledPattern() {
    return new CompiledPattern() {
      @Override
      public String getTypedVarPrefix() {
        return TokenBasedProfile.this.getTypedVarPrefix();
      }

      @Override
      public boolean isTypedVar(String str) {
        String prefix = TokenBasedProfile.this.getTypedVarPrefix();
        return str.startsWith(prefix);
      }
    };
  }

  @Override
  public void compile(PsiElement element, @NotNull GlobalCompilingVisitor globalVisitor) {
    element.accept(new MyCompilingVisitor(globalVisitor));
  }

  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new MyMatchingVisitor(globalVisitor);
  }

  @NotNull
  @Override
  public PsiElementVisitor createLexicalNodesFilter(@NotNull final LexicalNodesFilter filter) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (isLexicalNode(element)) {
          filter.setResult(true);
        }
      }
    };
  }

  protected boolean isLexicalNode(@NotNull PsiElement element) {
    return element instanceof PsiWhiteSpace || element instanceof PsiErrorElement;
  }

  @NotNull
  protected abstract String getTypedVarPrefix();

  protected abstract boolean isBlockElement(@NotNull PsiElement element);

  protected abstract boolean canBeVariable(PsiElement element);

  protected abstract boolean canBePatternVariable(PsiElement element);

  @NotNull
  protected abstract MatchingStrategy getMatchingStrategy(PsiElement root);

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return new MyReplaceHandler(context.getProject());
  }

  protected class MyCompilingVisitor extends PsiRecursiveElementVisitor {
    protected final GlobalCompilingVisitor myGlobalVisitor;

    protected MyCompilingVisitor(GlobalCompilingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
    }

    @Override
    public void visitElement(final PsiElement element) {
      final CompiledPattern pattern = myGlobalVisitor.getContext().getPattern();
      if (isBlockElement(element)) {
        myGlobalVisitor.setCodeBlockLevel(myGlobalVisitor.getCodeBlockLevel() + 1);

        if (myGlobalVisitor.getCodeBlockLevel() == 1) {
          initTopLevelElement(element, pattern);
        }
        else {
          super.visitElement(element);
        }

        myGlobalVisitor.setCodeBlockLevel(myGlobalVisitor.getCodeBlockLevel() - 1);
        return;
      }
      if (canBePatternVariable(element)) {
        if (pattern.isRealTypedVar(element)) {
          myGlobalVisitor.handle(element);
          final MatchingHandler handler = pattern.getHandler(element);
          handler.setFilter(new NodeFilter() {
            public boolean accepts(PsiElement other) {
              return canBeVariable(other);
            }
          });
          return;
        }
      }
      super.visitElement(element);
    }

    private void initTopLevelElement(PsiElement element, CompiledPattern pattern) {
      MatchingStrategy strategy = null;

      for (PsiElement el = element.getFirstChild(); el != null; el = el.getNextSibling()) {
        if (GlobalCompilingVisitor.getFilter().accepts(el)) {
          if (el instanceof PsiWhiteSpace) {
            myGlobalVisitor.addLexicalNode(el);
          }
        }
        else {
          el.accept(this);
          if (myGlobalVisitor.getCodeBlockLevel() == 1) {
            MatchingStrategy newstrategy = getMatchingStrategy(el);
            if (strategy == null) {
              strategy = newstrategy;
            }
            else if (strategy.getClass() != newstrategy.getClass()) {
              throw new UnsupportedPatternException(SSRBundle.message("different.strategies.for.top.level.nodes.error.message"));
            }
            final MatchingHandler matchingHandler = myGlobalVisitor.getContext().getPattern().getHandler(el);
            myGlobalVisitor.getContext().getPattern().setHandler(el, new TopLevelMatchingHandler(matchingHandler));
          }
        }
      }

      if (myGlobalVisitor.getCodeBlockLevel() == 1) {
        if (strategy == null) {
          strategy = new MatchingStrategy() {
            public boolean continueMatching(PsiElement start) {
              return true;
            }
          };
        }
        myGlobalVisitor.getContext().getPattern().setStrategy(strategy);
      }
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  protected class MyMatchingVisitor extends PsiElementVisitor {
    protected final GlobalMatchingVisitor myGlobalVisitor;

    public MyMatchingVisitor(GlobalMatchingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);

      if (canBePatternVariable(element)) {
        String text = element.getText();
        final boolean isTypedVar = myGlobalVisitor.getMatchContext().getPattern().isTypedVar(text);

        if (isTypedVar) {
          myGlobalVisitor.setResult(myGlobalVisitor.handleTypedElement(element, myGlobalVisitor.getElement()));
        }
        else {
          myGlobalVisitor.setResult(text.equals(myGlobalVisitor.getElement().getText()));
        }
      }
      else {
        myGlobalVisitor.setResult(myGlobalVisitor.matchSequentially(
          new FilteringNodeIterator(element.getFirstChild()),
          new FilteringNodeIterator(myGlobalVisitor.getElement().getFirstChild())
        ));
      }
    }
  }

  private static class MyReplaceHandler extends StructuralReplaceHandler {
    private final Project myProject;
    private final Map<ReplacementInfo, RangeMarker> myRangeMarkers = new HashMap<ReplacementInfo, RangeMarker>();

    private MyReplaceHandler(Project project) {
      myProject = project;
    }

    public void replace(ReplacementInfo info) {
      if (info.getMatchesCount() == 0) return;
      assert info instanceof ReplacementInfoImpl;
      MatchResult result = ((ReplacementInfoImpl)info).getMatchResult();
      //assert result instanceof TokenBasedMatchResult;
      PsiElement element = info.getMatch(0);
      if (element == null) return;
      PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
      assert file != null;
      //TokenBasedMatchResult tokenBasedResult = (TokenBasedMatchResult)result;
      //RangeMarker rangeMarker = tokenBasedResult.getRangeMarker();
      RangeMarker rangeMarker = myRangeMarkers.get(info);
      Document document = rangeMarker.getDocument();
      document.replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), info.getReplacement());
      PsiDocumentManager.getInstance(element.getProject()).commitDocument(document);
    }

    @Override
    public void prepare(ReplacementInfo info) {
      assert info instanceof ReplacementInfoImpl;
      MatchResult result = ((ReplacementInfoImpl)info).getMatchResult();
      PsiElement element = result.getMatch();
      PsiFile file = element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      //assert result instanceof TokenBasedMatchResult;
      //TokenBasedMatchResult res = (TokenBasedMatchResult)result;
      TextRange textRange = result.getMatchRef().getElement().getTextRange();
      assert textRange != null;
      RangeMarker rangeMarker = document.createRangeMarker(textRange);
      rangeMarker.setGreedyToLeft(true);
      rangeMarker.setGreedyToRight(true);
      myRangeMarkers.put(info, rangeMarker);
    }
  }
}
