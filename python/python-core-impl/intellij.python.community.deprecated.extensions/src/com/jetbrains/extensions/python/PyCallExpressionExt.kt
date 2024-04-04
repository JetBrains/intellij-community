// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.extensions.python;

import com.jetbrains.python.nameResolver.FQNamesProvider
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.PyCallExpression
import org.jetbrains.annotations.ApiStatus

/**
 * @deprecated moved to {@link com.jetbrains.python.extensions}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Moved to com.jetbrains.python")
fun PyCallExpression.isCalleeName(vararg names: FQNamesProvider): Boolean = NameResolverTools.isCalleeShortCut(this, *names)

