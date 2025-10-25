// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.Maven4RedundantParentCoordinatesInspection
import org.junit.Test

class Maven4RedundantParentCoordinatesInspectionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()

    fixture.enableInspections(Maven4RedundantParentCoordinatesInspection::class.java)
  }

  @Test
  fun testDoNotFireHighlightInMaven3() = runBlocking {
    assumeMaven3()
    val moduleFile = createModulePom("m1", """
      <parent>
        <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>1</version>
      </parent>
      <artifactId>m1</artifactId>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>  
""")
    checkHighlighting(moduleFile)
  }


  @Test
  fun testFireHighlightInMaven4() = runBlocking {
    assumeMaven4()
    val moduleFile = createModulePom("m1", """
     <parent>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><groupId>test</groupId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><artifactId>test</artifactId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><version>1</version></warning>
      </parent>
      <artifactId>m1</artifactId>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>  
""")
    checkHighlighting(moduleFile)
  }

  @Test
  fun testDoQuickFixForParent() = runBlocking {
    assumeMaven4()
    val moduleFile = createModulePom("m1", """
     <parent>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><groupId>test</groupId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><artifactId>test</artifactId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><caret><version>1</version></warning>
      </parent>
      <artifactId>m1</artifactId>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>  
""")
    checkHighlighting(moduleFile)

    val intention =  fixture.availableIntentions.singleOrNull{it.text == "Remove unnecessary tags"}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult(createPomXml("""
     <parent/>
      <artifactId>m1</artifactId>
"""), true)
  }

  @Test
  fun testDoQuickFixForParentWithRelativePath() = runBlocking {
    assumeMaven4()
    val module1File = createModulePom("m1", """
      <groupId>test</groupId>
      <version>1</version>
      <packaging>pom</packaging>
      <artifactId>m1</artifactId>
""")

    val module2File = createModulePom("m2", """
      <parent>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><groupId>test</groupId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><artifactId>m1</artifactId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><caret><version>1</version></warning>
        <relativePath>../m1/pom.xml</relativePath>
      </parent>
      <artifactId>m2</artifactId>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
        <module>m2</module>
      </modules>  
""")
    checkHighlighting(module2File)

    val intention =  fixture.availableIntentions.singleOrNull{it.text == "Remove unnecessary tags"}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult(createPomXml("""
      <parent>
          <relativePath>../m1/pom.xml</relativePath>
      </parent>
      <artifactId>m2</artifactId>
"""), true)
  }


}