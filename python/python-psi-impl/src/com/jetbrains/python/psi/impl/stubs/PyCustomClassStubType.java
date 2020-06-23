// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class PyCustomClassStubType<T extends PyCustomClassStub> implements PyCustomStubType<PyClass, T> {

  public static final ExtensionPointName<PyCustomClassStubType<? extends PyCustomClassStub>> EP_NAME =
    ExtensionPointName.create("Pythonid.customClassStubType");
}
