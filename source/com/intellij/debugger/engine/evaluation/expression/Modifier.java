/*
 * Interface Modifier
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public interface Modifier {
  boolean canInspect();

  boolean canSetValue();
  /**
   * sets the value to the expression
   */
  void setValue(Value value) throws ClassNotLoadedException, InvalidTypeException, EvaluateException;

  /**
   * @return the expected type of the expression or null is class was not loaded
   */
  Type getExpectedType() throws ClassNotLoadedException, EvaluateException;

  NodeDescriptor getInspectItem(Project project);
}