/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import com.intellij.openapi.util.Condition;

/**
 * @author max
 */
public class FilteringProcessor<T> implements Processor<T> {
  private final Condition<T> myFilter;
  private Processor<T> myProcessor;

  public FilteringProcessor(final Condition<T> filter, Processor<T> processor) {
    myFilter = filter;
    myProcessor = processor;
  }

  public boolean process(final T t) {
    if (!myFilter.value(t)) return true;
    return myProcessor.process(t);
  }
}
