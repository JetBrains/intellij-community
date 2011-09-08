package com.jetbrains.python.codeInsight.completion;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.PySeeingOriginalCompletionContributor;
import com.jetbrains.python.psi.PyStatementList;

/**
* A filter that matches iff our element or any its parent is preceded by a sibling element that satisfies all given constraints.
* Expects the {@link com.jetbrains.python.codeInsight.PySeeingOriginalCompletionContributor#ORG_ELT original element} to be provided.
* User: dcheryasov
* Date: Dec 3, 2009 10:23:06 AM
*/
/* NOTE: a better matching language would capture the matched elements, so that constraints on them are easy to add, like:
 * condition = inside(PyStatement.class).withPrevSibling(psiElement(PyConditionalStatement.class).withChild(PyElsePart.class))
 *             ^ we                      ^ the PyStatement which just matched                     ^ the PyConditionalStatement's
*/
public class PrecededByFilter implements ElementFilter {
  ElementPattern<? extends PsiElement>[] myConstraints;

  /**
   * Used to look for a statement right above us to which we might want to add a part.
   * Matches iff our element or any its parent is preceded by a sibling element that satisfies all given constraints.
   * Parents above PyStatementList and PsiFile levels are not considered, because no syntactic construction
   * spans multiple non-nested statements.
   * @param constraints which the preceding element must satisfy
   */
  public PrecededByFilter(ElementPattern<? extends PsiElement>... constraints) {
    myConstraints = constraints;
  }

  public boolean isAcceptable(Object what, PsiElement context) {
    if (!(what instanceof UserDataHolder)) return false; // can't dream to match
    PsiElement element = ((UserDataHolder)what).getUserData(PySeeingOriginalCompletionContributor.ORG_ELT);
    if (element == null) return false; // we're not from here
    ProcessingContext ctx = new ProcessingContext();
    // climb until "after what" matches
    while (element != null && !(element instanceof PsiFile) && !(element instanceof PyStatementList)) { // these have no worthy prev siblings
      PsiElement preceding = element.getPrevSibling();
      // TODO: make 'skip whitespece' configurable
      while (preceding instanceof PsiWhiteSpace) preceding = preceding.getPrevSibling();
      if (preceding != null) {
        boolean matched = true;
        for (ElementPattern<? extends PsiElement> constraint : myConstraints) {
          if (!constraint.accepts(preceding, ctx)) {
            matched = false;
            break;
          }
        }
        if (matched) return true;
      }
      element = element.getParent(); // bad luck, climb
    }
    // all above failed
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
