// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class SvnProgressCanceller implements ProgressTracker {
  @Nullable private final ProgressIndicator myIndicator;

  public SvnProgressCanceller() {
    this(null);
  }

  public SvnProgressCanceller(@Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  @Override
  public void checkCancelled() throws ProcessCanceledException {
    ProgressIndicator indicator = chooseNotNull(myIndicator, ProgressManager.getInstance().getProgressIndicator());

    if (indicator != null) {
      indicator.checkCanceled();
    }
  }

  @Override
  public void consume(ProgressEvent event) {
  }
}
