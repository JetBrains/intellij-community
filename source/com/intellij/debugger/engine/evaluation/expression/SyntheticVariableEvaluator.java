package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 25, 2004
 * Time: 2:53:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class SyntheticVariableEvaluator implements Evaluator{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.SyntheticVariableEvaluator");

  private final CodeFragmentEvaluator myCodeFragmentEvaluator;
  private final String myLocalName;

  public SyntheticVariableEvaluator(CodeFragmentEvaluator codeFragmentEvaluator, String localName) {
    myCodeFragmentEvaluator = codeFragmentEvaluator;
    myLocalName = localName;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return myCodeFragmentEvaluator.getValue(myLocalName, context.getDebugProcess().getVirtualMachineProxy());
  }

  public Modifier getModifier() {
    return new Modifier() {
      public boolean canInspect() {
        return false;
      }

      public boolean canSetValue() {
        return false;
      }

      public void setValue(Value value) throws EvaluateException {
        myCodeFragmentEvaluator.setValue(myLocalName, value);
      }

      public Type getExpectedType() {
        LOG.assertTrue(false);
        return null;
      }

      public NodeDescriptorImpl getInspectItem(Project project) {
        return null;
      }
    };
  }
}
