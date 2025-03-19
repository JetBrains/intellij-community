// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.DerivedResult;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public class MavenTaskFailedResultImpl implements DerivedResult {
  private final @NlsSafe String myError;

  public MavenTaskFailedResultImpl(@NlsSafe String error) {
    myError = error;
  }

  @Override
  public @NotNull FailureResult createFailureResult() {
    return new FailureResultImpl();
  }

  @Override
  public @NotNull EventResult createDefaultResult() {
    return new FailureResultImpl(myError);
  }
}
