package de.plushnikov.delegate;

import java.util.concurrent.Callable;

public class DelegateOnMethod implements Callable<Void> {
  private Callable<Void> calcDelegated() {
    return new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        return null;
      }
    };
  }

  public Void call() throws Exception {
    return this.calcDelegated().call();
  }
}