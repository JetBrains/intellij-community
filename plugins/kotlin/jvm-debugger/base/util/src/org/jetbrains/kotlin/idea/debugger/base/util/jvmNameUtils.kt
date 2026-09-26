// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import org.jetbrains.annotations.ApiStatus

/**
 * Converts a fully qualified name to a java internal name, with uses '/' as a separator.
 */
@ApiStatus.Internal
fun String.fqnToInternalName(): String = replace('.', '/')

@ApiStatus.Internal
fun String.internalNameToFqn(): String = replace('/', '.')
