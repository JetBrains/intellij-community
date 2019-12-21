// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Marks resolved element as part of a third-party sdk, like Google App Engine.
 */
@ApiStatus.Experimental
public interface PyThirdPartySdkDetector {

  ExtensionPointName<PyThirdPartySdkDetector> EP_NAME = ExtensionPointName.create("Pythonid.thirdPartySdkDetector");

  boolean isInThirdPartySdk(@NotNull PsiElement element);
}
