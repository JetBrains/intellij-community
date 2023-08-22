/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin

import org.junit.jupiter.api.Assertions
import kotlin.math.sign
import kotlin.reflect.KClass

internal fun Assertion<Boolean>.isTrue() = ifHasValue { actual ->
    Assertions.assertEquals(true, actual, message)
}

internal fun Assertion<Boolean>.isFalse() = ifHasValue { actual ->
    Assertions.assertEquals(false, actual, message)
}

internal fun <T> Assertion<T>.isNull() = ifHasValue { actual ->
    Assertions.assertNull(actual, message)
}

internal fun <T> Assertion<T>.isNotNull() = let {
    ifHasValue { actual -> Assertions.assertNotNull(actual, message) }
    map { it!! }
}

internal fun <T : Any, S : T> Assertion<T>.isInstanceOf(kClass: KClass<S>) = let {
    ifHasValue { actual -> Assertions.assertEquals(kClass, actual::class, message) }
    map { it as S }
}

internal fun <T : Any, S : T> Assertion<T>.isNotInstanceOf(kClass: KClass<S>) = apply {
    ifHasValue { actual -> Assertions.assertNotEquals(kClass, actual::class, message) }
}

internal fun <T> Assertion<T>.isEqualTo(expected: T) = ifHasValue { actual ->
    Assertions.assertEquals(expected, actual, message)
}

internal fun <T> Assertion<T>.isNotEqualTo(expected: T) = ifHasValue { actual ->
    Assertions.assertNotEquals(expected, actual, message)
}

internal fun <T> Assertion<T>.isZero() = ifHasValue { actual ->
    Assertions.assertEquals(0, actual, message)
}

internal fun <T> Assertion<Comparable<T>>.isGreaterThan(other: T) = ifHasValue { actual ->
    Assertions.assertEquals(1, actual.compareTo(other).sign, message)
}
