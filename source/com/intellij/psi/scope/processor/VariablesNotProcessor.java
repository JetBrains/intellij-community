package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Dec 12, 2002
 * Time: 4:09:53 PM
 * To change this template use Options | File Templates.
 */
public class VariablesNotProcessor extends VariablesProcessor{
  private final PsiVariable myVariable;

  public VariablesNotProcessor(PsiVariable var, boolean staticSensitive, List list){
    super(staticSensitive, list);
    myVariable = var;
  }

  public VariablesNotProcessor(PsiVariable var, boolean staticSensitive){
    this(var, staticSensitive, new ArrayList());
  }

  protected boolean check(PsiVariable var, PsiSubstitutor substitutor) {
    return var.getName() != null && var.getName().equals(myVariable.getName()) && !var.equals(myVariable);
  }
}
