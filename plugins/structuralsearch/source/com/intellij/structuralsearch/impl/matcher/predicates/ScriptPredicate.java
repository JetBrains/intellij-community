package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.idea.LoggerFactory;
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

import java.io.File;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends AbstractStringBasedPredicate {
  private final Script script;

  public ScriptPredicate(String name, String within) {
    super(name, within);
    File scriptFile = new File(within);
    GroovyShell shell = new GroovyShell();
    try {
      script = scriptFile.exists() ? shell.parse(scriptFile):shell.parse(within);
    } catch (Exception ex) {
      LoggerFactory.getInstance().getLoggerInstance(getClass().getName()).error(ex);
      throw new RuntimeException(ex);
    }
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