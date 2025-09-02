// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.ext.LanguageCaseCollector;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.remote.PyCredentialsContribution;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal

public abstract class CredentialsTypeExChecker {
  public boolean check(final @Nullable Sdk sdk) {
    if (sdk == null) {
      return false;
    }
    RemoteSdkAdditionalData data = ObjectUtils.tryCast(sdk.getSdkAdditionalData(), RemoteSdkAdditionalData.class);
    if (data == null) {
      return false;
    }
    return check(data);
  }

  public boolean check(RemoteSdkAdditionalData data) {
    final Ref<Boolean> result = Ref.create(false);
    data.switchOnConnectionType(new LanguageCaseCollector<PyCredentialsContribution>() {

      @Override
      protected void processLanguageContribution(PyCredentialsContribution languageContribution, Object credentials) {
        result.set(checkLanguageContribution(languageContribution));
      }
    }.collectCases(PyCredentialsContribution.class));
    return result.get();
  }

  protected abstract boolean checkLanguageContribution(PyCredentialsContribution languageContribution);
}
