package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.lookup.LookupItem;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Python keyword completion contributor.
 * User: dcheryasov
 * Date: Sep 8, 2008
 */
public class PyKeywordCompletionContributor extends CompletionContributor {
  protected static final PsiElementPattern.Capture<PsiElement> AFTER_QUALIFIED_REFERENCE =
    psiElement().afterLeaf(psiElement().andOr(psiElement().withText(".")))
  ;

  /**
   * Allows element if it has a parent of given class(es).
   */
  protected static class ParentClassFilter implements ElementFilter {
    protected Class<? extends PsiElement>[] my_accepted_parents;

    protected ParentClassFilter(Class<? extends PsiElement>... parents) {
      my_accepted_parents = parents;
    }

    public boolean isAcceptable(final Object element, final PsiElement context) {
      return PsiTreeUtil.getParentOfType(context, my_accepted_parents) != null;
    }

    public boolean isClassAcceptable(final Class hintClass) {
      return true;
    }
  }

  protected static class PrevSiblingClassFilter implements ElementFilter {
    protected Class<? extends PsiElement>[] my_accepted;

    protected PrevSiblingClassFilter(Class<? extends PsiElement>... prevs) {
      my_accepted = prevs;
    }

    public boolean isAcceptable(final Object element, final PsiElement context) {
      PsiElement here_stmt = PsiTreeUtil.getParentOfType(context, PyStatement.class);
      if (here_stmt == null) return false;
      PsiElement prev = here_stmt;
      do {
        prev = prev.getPrevSibling();
      } while (prev != null && !(prev instanceof PyStatement));
      for (Class cls : my_accepted) {
        if (cls.isInstance(prev)) return true;
      }
      return false;
    }

    public boolean isClassAcceptable(final Class hintClass) {
      return true;
    }
  }

  protected static final FilterPattern IN_LOOP = new FilterPattern(new ParentClassFilter(PyWhileStatement.class, PyForStatement.class));
  protected static final FilterPattern IN_TRY = new FilterPattern(new ParentClassFilter(PyTryExceptStatement.class));
  protected static final FilterPattern IN_DEF = new FilterPattern(new ParentClassFilter(PyFunction.class));
  protected static final FilterPattern IN_IF = new FilterPattern(new ParentClassFilter(PyIfStatement.class)); // TODO: handle "if. elif, but not else"
  protected static final FilterPattern IN_EXPR = new FilterPattern(new ParentClassFilter(PyExpressionStatement.class));
  protected static final FilterPattern IN_IMPORT_STMT = new FilterPattern(new ParentClassFilter(PyImportStatement.class, PyFromImportStatement.class));
  protected static final FilterPattern IN_IMPORT_ELT_OR_WITH = new FilterPattern(new ParentClassFilter(PyImportElement.class, PyWithStatement.class));

  protected static final FilterPattern AFTER_IF = new FilterPattern(new PrevSiblingClassFilter(PyIfStatement.class));
  protected static final FilterPattern AFTER_LOOP = new FilterPattern(new PrevSiblingClassFilter(PyWhileStatement.class, PyForStatement.class));
  protected static final FilterPattern AFTER_TRY = new FilterPattern(new PrevSiblingClassFilter(PyTryExceptStatement.class));


  protected static void addKeywords(@NonNls @NotNull String[] words, TailType tail, CompletionParameters parameters, final CompletionResultSet result) {
    final LookupElementFactory factory = LookupElementFactory.getInstance();
    for (String s : words) {
      LookupItem<String> elt = (LookupItem<String>)factory.createLookupElement(s);
      elt.setBold();
      elt.setTailType(tail);
      result.addElement(elt);
    }
  }

  protected void addBasic() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance()).andNot(AFTER_QUALIFIED_REFERENCE).andNot(IN_IMPORT_STMT),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {
            "assert", "def", "class", "for", "del", "exec", "from", "import", "lambda", "print", "raise", "while", "with"
          };
          final @NonNls String[] colon_strings = {"if", "try"};
          final @NonNls String[] just_strings = {"pass"};
          addKeywords(space_strings, TailType.SPACE, parameters, result);
          addKeywords(colon_strings, TailType.CASE_COLON, parameters, result);
          addKeywords(just_strings, TailType.NONE, parameters, result);
        }
      }
    );
  }

  protected void addWithinLoops() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance()).andNot(AFTER_QUALIFIED_REFERENCE).andOr(IN_LOOP, AFTER_LOOP),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] strings = {"break", "continue"};
          addKeywords(strings, TailType.NONE, parameters, result);
        }
      }
    );
  }

  protected void addWithinFuncs() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance()).andNot(AFTER_QUALIFIED_REFERENCE).and(IN_DEF),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"global", "return", "yield"};
          addKeywords(space_strings, TailType.SPACE, parameters, result);
        }
      }
    );
  }

  protected void addWithinIf() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance()).andNot(AFTER_QUALIFIED_REFERENCE).andOr(IN_IF, AFTER_IF),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"elif"};
          addKeywords(space_strings, TailType.SPACE, parameters, result);
        }
      }
    );
  }

  protected void addWithinTry() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance()).andNot(AFTER_QUALIFIED_REFERENCE).andOr(IN_TRY, AFTER_TRY),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"except"};
          final @NonNls String[] colon_strings = {"finally"};
          addKeywords(space_strings, TailType.SPACE, parameters, result);
          addKeywords(colon_strings, TailType.CASE_COLON, parameters, result);
        }
      }
    );
  }

  protected void addElse() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance())
        .andNot(AFTER_QUALIFIED_REFERENCE).andOr(IN_LOOP, IN_IF, IN_TRY, AFTER_LOOP, AFTER_IF, AFTER_TRY),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] colon_strings = {"else"};
          addKeywords(colon_strings, TailType.CASE_COLON, parameters, result);
        }
      }
    );
  }

  protected void addWithinExpr() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance()).andNot(AFTER_QUALIFIED_REFERENCE).andOr(IN_EXPR),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"not", "and", "or", "is", "in"};
          addKeywords(space_strings, TailType.SPACE, parameters, result);
        }
      }
    );
  }

  protected void addAs() {
    extend(
      CompletionType.BASIC, psiElement().withLanguage(PythonLanguage.getInstance())
        .andNot(AFTER_QUALIFIED_REFERENCE).and( IN_IMPORT_ELT_OR_WITH),
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"as"};
          addKeywords(space_strings, TailType.SPACE, parameters, result);
        }
      }
    );
  }

  public PyKeywordCompletionContributor() {
    addBasic();
    addWithinLoops();
    addWithinFuncs();
    addWithinIf();
    addWithinTry();
    addWithinExpr();
    addElse();
    addAs();
  }
}
