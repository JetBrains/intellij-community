package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;

public interface ProgressIndicatorEx extends ProgressIndicator {

  void addStateDelegate(ProgressIndicatorEx delegate);

}
