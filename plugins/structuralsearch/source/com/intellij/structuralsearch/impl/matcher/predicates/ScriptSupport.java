package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.idea.LoggerFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.File;

/**
 * @author Maxim.Mossienko
 * Date: 11.06.2009
 * Time: 16:25:12
 */
public class ScriptSupport {
  private final Script script;

  public ScriptSupport(String text) {
    File scriptFile = new File(text);
    GroovyShell shell = new GroovyShell();
    try {
      script = scriptFile.exists() ? shell.parse(scriptFile):shell.parse(text);
    } catch (Exception ex) {
      LoggerFactory.getInstance().getLoggerInstance(getClass().getName()).error(ex);
      throw new RuntimeException(ex);
    }
  }

  public String evaluate(MatchResultImpl result, PsiElement context) {
    try {
      Binding binding = new Binding();

      if (result != null) {
        for(MatchResult r:result.getMatches()) {
          binding.setVariable(r.getName(),r.getMatchRef().getElement());
        }
      }

      if (context == null) {
        context = result.getMatchRef().getElement();
      }
      if (context instanceof PsiIdentifier) context = context.getParent();
      binding.setVariable("__context__", context);
      script.setBinding(binding);

      Object o = script.run();
      //if (o instanceof String) return "\"" + o + "\"";
      return String.valueOf(o);
    } catch (GroovyRuntimeException ex) {
      return StringUtil.convertLineSeparators(ex.getLocalizedMessage());
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
