package com.intellij.structuralsearch.impl.matcher.handlers;

/**
 * @author Eugene.Kudelevsky
 */
public interface DelegatingHandler {
  MatchingHandler getDelegate();
}
