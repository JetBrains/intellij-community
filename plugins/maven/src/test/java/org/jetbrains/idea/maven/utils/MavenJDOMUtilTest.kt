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

import com.intellij.idea.IJIgnore
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.updateProjectSubFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.IOException
import java.nio.file.Files

@IJIgnore(issue = "IDEA-386161")
@TestApplication
class MavenJDOMUtilTest {
  private val maven by mavenFixture()

  @Test
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
    val fileName = "foo.xml"
    val filePath = maven.projectPath.resolve(fileName)
    val f = if (!Files.exists(filePath)) {
      maven.createProjectSubFile(fileName, xml)
    }
    else {
      maven.updateProjectSubFile(fileName, xml)
    }

    maven.refreshFiles(listOf(f))

    val el = MavenJDOMUtil.read(f, object : MavenJDOMUtil.ErrorHandler {
      override fun onReadError(e: IOException?) {
        throw RuntimeException(e)
      }

      override fun onSyntaxError(message: String, startOffset: Int, endOffset: Int) {
        fail("syntax error")
      }
    })

    return MavenJDOMUtil.findChildValueByPath(el, valuePath)
  }
}
