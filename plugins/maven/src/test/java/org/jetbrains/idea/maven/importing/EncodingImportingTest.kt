// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class EncodingImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  @Test
  fun testEncodingDefinedByProperty() = runBlocking {
    val text = byteArrayOf(-12, -59, -53, -45, -44) // Russian text in koi8-r encoding.

    val file = maven.createProjectSubFile("src/main/resources/A.txt")
    edtWriteAction {
      file.setBinaryContent(text)
    }

    maven.importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

        <properties>
        <project.build.sourceEncoding>koi8-r</project.build.sourceEncoding>
        </properties>
        """.trimIndent())

    val loadedText = VfsUtil.loadText(file)

    assert(loadedText == String(text, charset("koi8-r")))
  }

  @Test
  fun testEncodingDefinedByPluginConfig() = runBlocking {
    val text = byteArrayOf(-12, -59, -53, 45, -44) // Russian text in koi8-r encoding.

    val file = maven.createProjectSubFile("src/main/resources/A.txt")
    edtWriteAction {
      file.setBinaryContent(text)
    }

    maven.importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

          <build>
            <plugins>
              <plugin>
                <artifactId>maven-resources-plugin</artifactId>
                <configuration>
                  <encoding>koi8-r</encoding>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """.trimIndent())

    val loadedText = VfsUtil.loadText(file)

    assert(loadedText == String(text, charset("koi8-r")))
  }
}
