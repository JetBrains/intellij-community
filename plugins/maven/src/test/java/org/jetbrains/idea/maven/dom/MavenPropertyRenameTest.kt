// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenPropertyRenameTest : MavenDomTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testRenamingPropertyTag() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{foo}</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    assertRenameResult("xxx",
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>aaa${'$'}{foo}bbb</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    assertRenameResult("xxx",
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>aaa${'$'}{foo}bbb</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    assertRenameResult("xxxxx",
                       """
                         <groupId>test</groupId>
                         <artifactId>module1</artifactId>
                         <version>1</version>
                         <name>aaa${'$'}{xxxxx}bbb</name>
                         <properties>
                           <xxxxx>value</xxxxx>
                         </properties>
                         """.trimIndent())

    assertRenameResult("xx",
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{f<caret>oo}</name>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       """.trimIndent())

    assertRenameResult("xxx",
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <nam<caret>e>foo</name>
                       <description>${'$'}{project.name}</description>
                       """.trimIndent())

    assertCannotRename()
  }

  @Test
  fun testDoNotRenameModelPropertiesFromReference() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>foo</name>
                       <description>${'$'}{proje<caret>ct.name}</description>
                       """.trimIndent())

    assertCannotRename()
  }

  @Test
  fun testDoNotRenameModelPropertiesTag() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>foo</name>
                       <properti<caret>es></properties>
                       """.trimIndent())

    assertCannotRename()
  }
}
