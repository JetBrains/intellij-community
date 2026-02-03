// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused")

package org.jetbrains.uast

/*
 * Adapters of the UAST base interfaces.
 *
 * Can be useful for UAST plugins written in Kotlin and may be the only way to implement
 * needed interfaces in other JVM-languages such as Scala, where JVM clashes happen
 * when trying to inherit from some UAST interfaces.
 *
 * Provides:
 *  - Elimination of some possible JVM clashes
 *  - Inherited default implementations from UAST interfaces
 *  - Kotlin delegation mechanism which helps implement PSI interfaces by some delegate
 */

abstract class UElementAdapter : UElement

abstract class UExpressionAdapter : UExpression

abstract class UAnnotatedAdapter : UAnnotated

abstract class ULabeledAdapter : ULabeled

abstract class UResolvableAdapter : UResolvable

