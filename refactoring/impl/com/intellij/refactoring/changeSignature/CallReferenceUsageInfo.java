package com.intellij.refactoring.changeSignature;

import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CallReferenceUsageInfo extends UsageInfo {
  private final PsiCallReference myReference;

  public CallReferenceUsageInfo(@NotNull PsiCallReference reference) {
    super(reference);
    myReference = reference;
  }

  public PsiCallReference getReference() {
    return myReference;
  }
}
