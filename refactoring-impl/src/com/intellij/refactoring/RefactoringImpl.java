/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.openapi.util.Ref;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public abstract class RefactoringImpl<T extends BaseRefactoringProcessor> implements Refactoring {
  protected final T myProcessor;

  public RefactoringImpl(T refactoringProcessor) {
    myProcessor = refactoringProcessor;
  }

  public void setPreviewUsages(boolean value) {
    myProcessor.setPreviewUsages(value);
  }

  public boolean isPreviewUsages() {
    return myProcessor.isPreviewUsages();
  }

  public void setInteractive(Runnable prepareSuccessfulCallback) {
    myProcessor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
  }

  public boolean isInteractive() {
    return myProcessor.myPrepareSuccessfulSwingThreadCallback != null;
  }

  public UsageInfo[] findUsages() {
    return myProcessor.findUsages();
  }

  public boolean preprocessUsages(Ref<UsageInfo[]> usages) {
    return myProcessor.preprocessUsages(usages);
  }

  public boolean shouldPreviewUsages(UsageInfo[] usages) {
    return myProcessor.isPreviewUsages(usages);
  }

  public void doRefactoring(UsageInfo[] usages) {
    myProcessor.execute(usages);
  }

  public void run() {
    myProcessor.run();
  }


}
