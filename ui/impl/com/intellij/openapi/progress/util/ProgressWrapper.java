/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 26, 2007
 * Time: 1:56:28 PM
 */
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;

public class ProgressWrapper extends ProgressIndicatorBase {
    private final ProgressIndicator myOriginal;

    public ProgressWrapper(final ProgressIndicator original) {
      myOriginal = original;
    }

    public boolean isCanceled() {
      return myOriginal.isCanceled();
    }
  }
