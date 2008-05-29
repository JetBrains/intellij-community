package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends AbstractStringBasedPredicate {
  private final Script script;

  public ScriptPredicate(String name, String within) {
    super(name, within);
    script = new GroovyShell().parse(within);
  }

  public boolean match(PsiElement node, PsiElement match, int start, int end, MatchContext context) {
    try {
      if (match == null) return false;
      Binding binding = new Binding();

      if (context.hasResult()) {
        final MatchResultImpl result = context.getResult();
        for(MatchResult r:result.getMatches()) {
          binding.setVariable(r.getName(),r.getMatchRef().getElement());
        }
      }

      if (match instanceof PsiIdentifier) match = match.getParent();
      binding.setVariable("__context__",match);
      script.setBinding(binding);

      final Object value = script.run();
      return Boolean.TRUE.equals(value);
    } catch (GroovyRuntimeException ex) {
      return false;
    }
  }

  public static String checkValidScript(String script) {
    try {
      final Object o = new GroovyShell().parse(script);
      return null;
    } catch (CompilationFailedException ex) {
      return ex.getLocalizedMessage();
    }
  }
}