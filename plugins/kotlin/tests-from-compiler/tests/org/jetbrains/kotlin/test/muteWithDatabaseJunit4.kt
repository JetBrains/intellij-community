// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused") // used at runtime

package org.jetbrains.kotlin.test

import junit.framework.TestCase

/**
 * The bundled configuration has to be runned against muteWithDatabase.kt, but the trunk against muteWithDatabaseJunit4.kt because
 * of the commit in the compiler https://jetbrains.team/p/kt/repositories/kotlin/revision/3542c555adc75cbe627a77c22420ddb5fedeb826
 * For more details see https://jetbrains.slack.com/archives/C03GRK1D9EE/p1728398737239909
 */
@Suppress("CONFLICTING_OVERLOADS")
fun TestCase.runTest(test: () -> Unit) {
    test.invoke()
}
