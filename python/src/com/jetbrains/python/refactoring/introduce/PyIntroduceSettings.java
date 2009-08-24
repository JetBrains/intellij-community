package com.jetbrains.python.refactoring.introduce;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyExpression;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:22:59 PM
 */
public interface PyIntroduceSettings {
  String getName();
  Project getProject();
  PyExpression getExpression();
  boolean doReplaceAllOccurrences();
  boolean isOK();
}
