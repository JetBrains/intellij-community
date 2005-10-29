/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

/**
 * @author max
 */
public class FilteringProcessor<T> implements Processor<T> {
  private final Filter<T> myFilter;
  private Processor<T> myProcessor;

  public FilteringProcessor(final Filter<T> filter, Processor<T> processor) {
    myFilter = filter;
    myProcessor = processor;
  }

  public boolean process(final T t) {
    if (!myFilter.accepts(t)) return true;
    return myProcessor.process(t);
  }
}
