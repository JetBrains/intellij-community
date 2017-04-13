/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.universalTests

import com.jetbrains.python.fixtures.PyTestCase
import org.junit.Assert
import org.junit.Test

/**
 * @author Ilya.Kazakevich
 */
class PyTestRunnerUtilsKtTest : PyTestCase() {
  @Test
  fun testGetParsedAdditionalArguments() {
    var list = getParsedAdditionalArguments(myFixture.project, "-v --color=red -m 'spam and eggs'")
    Assert.assertEquals("List parsed incorrectly", listOf("-v", "--color=red", "-m", "spam and eggs"), list)

    list = getParsedAdditionalArguments(myFixture.project, "--eggs=spam --foo=\"eggs and spam\"")
    Assert.assertEquals("List parsed incorrectly", listOf("--eggs=spam", "--foo=eggs and spam"), list)

  }
}