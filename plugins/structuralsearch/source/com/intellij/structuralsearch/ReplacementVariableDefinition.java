package com.intellij.structuralsearch;

/**
 * @author Maxim.Mossienko
 * Date: Mar 19, 2004
 * Time: 5:36:32 PM
 */
public class ReplacementVariableDefinition extends NamedScriptableDefinition {
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ReplacementVariableDefinition)) return false;
    return super.equals(o);
  }
}