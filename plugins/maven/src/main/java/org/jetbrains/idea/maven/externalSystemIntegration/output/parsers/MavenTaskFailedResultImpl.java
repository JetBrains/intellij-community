// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.DerivedResult;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public class MavenTaskFailedResultImpl implements DerivedResult {
  private @NlsSafe final String myError;

  public MavenTaskFailedResultImpl(@NlsSafe String error) {
    myError = error;
  }

  @NotNull
  @Override
  public FailureResult createFailureResult() {
    return new FailureResultImpl();
  }

  @NotNull
  @Override
  public EventResult createDefaultResult() {
    return new FailureResultImpl(myError);
  }
}
