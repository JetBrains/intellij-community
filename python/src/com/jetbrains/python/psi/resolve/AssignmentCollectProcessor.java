package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AssignmentCollectProcessor implements PsiScopeProcessor {
  /**
   * Collects all assignments in context above given element, if they match given naming pattern.
   * Used to track creation of attributes by assignment (e.g in constructor).
   */
  List<? extends PyExpression> my_qualifier;
  List<PyExpression> my_result;
  Set<String> my_seen_names;

  /**
   * Creates an instance to collect assignments of attributes to the object identified by 'qualifier'.
   * E.g. if qualifier = {"foo", "bar"} then assignments like "foo.bar.baz = ..." will be considered.
   * The collection continues up to the point of latest redefinition of the object identified by 'qualifier',
   * that is, up to the point of something like "foo.bar = ..." or "foo = ...".
   *
   * @param qualifier qualifying names, outermost first; must not be empty.
   */
  public AssignmentCollectProcessor(@NotNull List<? extends PyExpression> qualifier) {
    assert qualifier.size() > 0;
    my_qualifier = qualifier;
    my_result = new ArrayList<PyExpression>();
    my_seen_names = new HashSet<String>();
  }

  public boolean execute(final PsiElement element, final ResolveState state) {
    if (element instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)element;
      for (PyExpression ex : assignment.getTargets()) {
        if (ex instanceof PyTargetExpression) {
          final PyTargetExpression target = (PyTargetExpression)ex;
          List<PyTargetExpression> quals = PyResolveUtil.unwindQualifiers(target);
          if (quals != null) {
            if (quals.size() == my_qualifier.size() + 1 && PyResolveUtil.pathsMatch(quals, my_qualifier)) {
              // a new attribute follows last qualifier; collect it.
              PyTargetExpression last_elt = quals.get(quals.size() - 1); // last item is the outermost, new, attribute.
              String last_elt_name = last_elt.getName();
              if (!my_seen_names.contains(last_elt_name)) { // no dupes, only remember the latest
                my_result.add(last_elt);
                my_seen_names.add(last_elt_name);
              }
            }
            else if (quals.size() < my_qualifier.size() + 1 && PyResolveUtil.pathsMatch(my_qualifier, quals)) {
              // qualifier(s) get redefined; collect no more.
              return false;
            }
          }
        }

      }
    }
    return true; // nothing interesting found, continue
  }

  /**
   * @return a collection of exressions (parts of assignment expressions) where new attributes were defined. E.g. for "a.b.c = 1",
   *         the expression for 'c' is in the result.
   */
  @NotNull
  public Collection<PyExpression> getResult() {
    return my_result;
  }

  public <T> T getHint(final Key<T> hintKey) {
    return null;
  }

  public void handleEvent(final Event event, final Object associated) {
    // empty
  }

}
