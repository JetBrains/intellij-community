package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateException;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 20, 2004
 * Time: 7:12:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class BreakException extends EvaluateException{
  private final String myLabelName;

  public BreakException(String labelName) {
    super("No loop statements labeled with '" + labelName + "'", null);
    myLabelName = labelName;
  }

  public String getLabelName() {
    return myLabelName;
  }
}
