// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;

/**
 * Base class for elements that have a qualified name (classes and functions).
 */
@ApiStatus.Experimental
public interface PyAstQualifiedNameOwner extends PyAstElement {
}
