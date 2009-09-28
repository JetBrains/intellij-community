package com.theoryinpractice.testng.ui;

import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.Disposable;
import com.theoryinpractice.testng.model.TestTreeBuilder;

public class Animator extends TestsProgressAnimator {
  public Animator(final Disposable parentDisposable, final TestTreeBuilder builder) {
    super(parentDisposable);
    init(builder);
  }
}
