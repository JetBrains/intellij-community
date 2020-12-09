@file:Suppress("USELESS_CAST")

package com.jetbrains.packagesearch.intellij.plugin.api

import arrow.core.Either
import assertk.Assert
import assertk.assertions.support.expected
import assertk.assertions.support.show

fun <T> Assert<Either<T, *>>.isLeft(): Assert<Either.Left<T>> = transform { actual ->
    if (actual !is Either.Left) expected("to be: ${show("Either.Left")} but was: ${show("Either.Right")}")
    actual as Either.Left<T>
}

fun <T> Assert<Either.Left<T>>.leftValue(): Assert<T> = transform { actual -> actual.a }

fun <T> Assert<Either<*, T>>.isRight(): Assert<Either.Right<T>> = transform { actual ->
    if (actual !is Either.Right) expected("to be: ${show("Either.Right")} but was: ${show("Either.Left")}")
    actual as Either.Right<T>
}

fun <T> Assert<Either.Right<T>>.rightValue(): Assert<T> = transform { actual -> actual.b }
