package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.DoubleArrayList;

public interface ProgressIndicatorEx extends ProgressIndicator {

  void addStateDelegate(ProgressIndicatorEx delegate);

  void initStateFrom(ProgressIndicatorEx indicator);

  Stack<String> getTextStack();

  DoubleArrayList getFractionStack();

  Stack<String> getText2Stack();

  int getNonCancelableCount();

  boolean isModalityEntered();
}
