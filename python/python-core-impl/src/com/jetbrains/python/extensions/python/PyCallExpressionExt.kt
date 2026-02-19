// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.extensions.python

import com.jetbrains.python.nameResolver.FQNamesProvider
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyPossibleClassMember

/**
 * Checks if ``foo = SomeExpr()`` where foo is class attribute
 */
val PyCallExpression.isClassAttribute: Boolean
  get() =
    (parent as? PyAssignmentStatement)?.targets?.filterIsInstance<PyPossibleClassMember>()?.any { it.containingClass != null } == true

/**
 * Checks if callee has certain name. Only name is checked, so import aliases aren't supported, but it works pretty fast
 */
fun PyCallExpression.isCalleeName(vararg names: FQNamesProvider): Boolean = NameResolverTools.isCalleeShortCut(this, *names)

