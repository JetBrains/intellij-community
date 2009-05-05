/*
 * @author max
 */
package com.intellij.util;

public class AdapterProcessor<T, S> implements Processor<T> {
  private final Processor<S> myAdaptee;
  private final Function<T, S> myConversion;

  public AdapterProcessor(Processor<S> adaptee, Function<T, S> conversion) {
    myAdaptee = adaptee;
    myConversion = conversion;
  }

  public boolean process(T t) {
    return myAdaptee.process(myConversion.fun(t));
  }
}
