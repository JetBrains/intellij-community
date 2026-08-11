// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertCannotRename
import org.jetbrains.idea.maven.fixtures.assertRenameResult
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPropertyRenameTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testRenamingPropertyTag() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{foo}</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    maven.assertRenameResult("xxx",
                       """
                         <groupId>test</groupId>
                         <artifactId>module1</artifactId>
                         <version>1</version>
                         <name>${'$'}{xxx}</name>
                         <properties>
                           <xxx>value</xxx>
                         </properties>
                         """.trimIndent())
  }

  @Test
  fun testDoNotRuinTextAroundTheReferenceWhenRenaming() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>aaa${'$'}{foo}bbb</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    maven.assertRenameResult("xxx",
                       """
                         <groupId>test</groupId>
                         <artifactId>module1</artifactId>
                         <version>1</version>
                         <name>aaa${'$'}{xxx}bbb</name>
                         <properties>
                           <xxx>value</xxx>
                         </properties>
                         """.trimIndent())
  }

  @Test
  fun testRenamingChangesTheReferenceAccordingly() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>aaa${'$'}{foo}bbb</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    maven.assertRenameResult("xxxxx",
                       """
                         <groupId>test</groupId>
                         <artifactId>module1</artifactId>
                         <version>1</version>
                         <name>aaa${'$'}{xxxxx}bbb</name>
                         <properties>
                           <xxxxx>value</xxxxx>
                         </properties>
                         """.trimIndent())

    maven.assertRenameResult("xx",
                       """
                         <groupId>test</groupId>
                         <artifactId>module1</artifactId>
                         <version>1</version>
                         <name>aaa${'$'}{xx}bbb</name>
                         <properties>
                           <xx>value</xx>
                         </properties>
                         """.trimIndent())
  }

  @Test
  fun testRenamingPropertyFromReference() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{f<caret>oo}</name>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       """.trimIndent())

    maven.assertRenameResult("xxx",
                       """
                         <groupId>test</groupId>
                         <artifactId>module1</artifactId>
                         <version>1</version>
                         <name>${'$'}{xxx}</name>
                         <properties>
                           <xxx>value</xxx>
                         </properties>
                         """.trimIndent())
  }

  @Test
  fun testDoNotRenameModelProperties() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <nam<caret>e>foo</name>
                       <description>${'$'}{project.name}</description>
                       """.trimIndent())

    maven.assertCannotRename()
  }

  @Test
  fun testDoNotRenameModelPropertiesFromReference() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>foo</name>
                       <description>${'$'}{proje<caret>ct.name}</description>
                       """.trimIndent())

    maven.assertCannotRename()
  }

  @Test
  fun testDoNotRenameModelPropertiesTag() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>foo</name>
                       <properti<caret>es></properties>
                       """.trimIndent())

    maven.assertCannotRename()
  }
}
