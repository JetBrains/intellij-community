// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents positional-only parameters delimiter introduced in Python 3.8 (PEP 570).
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2019.2")
public interface PySlashParameter extends PyParameter, StubBasedPsiElement<PySlashParameterStub> {
}
