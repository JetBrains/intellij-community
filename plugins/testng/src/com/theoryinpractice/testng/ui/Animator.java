package com.theoryinpractice.testng.ui;

import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.theoryinpractice.testng.model.TestTreeBuilder;

public class Animator extends TestsProgressAnimator
{
    public Animator(final TestTreeBuilder builder) {
      init(builder);
    }
}
