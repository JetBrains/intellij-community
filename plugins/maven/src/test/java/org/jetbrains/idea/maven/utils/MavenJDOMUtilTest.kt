/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.MavenTestCase
import kotlinx.coroutines.runBlocking
import java.io.IOException

class MavenJDOMUtilTest : MavenTestCase() {
  fun testReadingValuesWithComments() = runBlocking {
    assertEquals("aaa", readValue("<root><foo>aaa<!--a--></foo></root>", "foo"))
    assertEquals("aaa", readValue("""
  <root><foo>
  aaa<!--a--></foo></root>
  """.trimIndent(), "foo"))
    assertEquals("aaa", readValue("""
  <root><foo>aaa<!--a-->
  </foo></root>
  """.trimIndent(), "foo"))
    assertEquals("aaa", readValue("""
                                    <root><foo>
                                    aaa
                                    <!--a-->
                                    </foo></root>
                                    """.trimIndent(), "foo"))
  }

  private suspend fun readValue(xml: String, valuePath: String): String? {
    val f = createProjectSubFile("foo.xml", xml)

    val el = MavenJDOMUtil.read(f, object : MavenJDOMUtil.ErrorHandler {
      override fun onReadError(e: IOException?) {
        throw RuntimeException(e)
      }

      override fun onSyntaxError() {
        fail("syntax error")
      }
    })

    return MavenJDOMUtil.findChildValueByPath(el, valuePath)
  }
}
