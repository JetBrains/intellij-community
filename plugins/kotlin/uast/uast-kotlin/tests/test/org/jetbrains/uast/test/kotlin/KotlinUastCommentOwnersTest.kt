// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.junit.Test


class KotlinUastCommentOwnersTest : AbstractKotlinCommentsTest()  {

    @Test
    fun testCommentOwners() = doTest("CommentOwners")

    @Test
    fun testComments() = doTest("Comments")
}