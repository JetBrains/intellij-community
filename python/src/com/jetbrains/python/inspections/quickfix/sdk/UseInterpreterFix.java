// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix.sdk;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.sdk.PySdkPopupFactory;
import org.jetbrains.annotations.NotNull;

public abstract class UseInterpreterFix<T extends Sdk> implements LocalQuickFix {
  @NotNull
  protected final T mySdk;

  protected UseInterpreterFix(@NotNull T sdk) {
    mySdk = sdk;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter");
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return PyPsiBundle.message("INSP.interpreter.use.interpreter", PySdkPopupFactory.Companion.shortenNameInPopup(mySdk, 75));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}