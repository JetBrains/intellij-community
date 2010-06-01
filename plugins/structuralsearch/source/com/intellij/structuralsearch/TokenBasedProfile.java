package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import org.jetbrains.annotations.NotNull;

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
    return element instanceof PsiWhiteSpace;
  }

  @NotNull
  protected abstract String getTypedVarPrefix();

  protected abstract boolean isBlockElement(@NotNull PsiElement element);

  protected abstract boolean canBeVariable(PsiElement element);

  @NotNull
  protected abstract MatchingStrategy getMatchingStrategy(PsiElement root);

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
      if (pattern.isRealTypedVar(element)) {
        if (element.getChildren().length == 0) {
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

  protected static class MyMatchingVisitor extends PsiElementVisitor {
    protected final GlobalMatchingVisitor myGlobalVisitor;

    public MyMatchingVisitor(GlobalMatchingVisitor globalVisitor) {
      myGlobalVisitor = globalVisitor;
    }

    public GlobalMatchingVisitor getGlobalVisitor() {
      return myGlobalVisitor;
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);

      PsiElement[] children = element.getChildren();
      if (children.length == 0) {
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
          new ArrayBackedNodeIterator(children),
          new ArrayBackedNodeIterator(myGlobalVisitor.getElement().getChildren())
        ));
      }
    }
  }
}
