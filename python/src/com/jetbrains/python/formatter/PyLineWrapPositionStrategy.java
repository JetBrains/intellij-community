package com.jetbrains.python.formatter;

import com.intellij.openapi.editor.GenericLineWrapPositionStrategy;

/**
 * @author yole
 */
public class PyLineWrapPositionStrategy extends GenericLineWrapPositionStrategy {
  public PyLineWrapPositionStrategy() {
    // Commas.
    addRule(new Rule(',', WrapCondition.AFTER, Rule.DEFAULT_WEIGHT * 1.4));

    // Symbols to wrap either before or after.
    addRule(new Rule(' '));
    addRule(new Rule('\t'));

    // Symbols to wrap after.
    addRule(new Rule('(', WrapCondition.AFTER));
    addRule(new Rule('[', WrapCondition.AFTER));
    addRule(new Rule('{', WrapCondition.AFTER));

    // Symbols to wrap before
    addRule(new Rule('.', WrapCondition.BEFORE));
  }
}
