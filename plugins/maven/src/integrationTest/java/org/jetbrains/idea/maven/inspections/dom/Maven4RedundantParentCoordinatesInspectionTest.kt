// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.Maven4RedundantParentCoordinatesInspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class Maven4RedundantParentCoordinatesInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(mavenVersion = mavenVersion, modelVersion = modelVersion)

  @Test
  fun testDoNotFireHighlightInMaven3() = runBlocking {
    maven.assumeMaven3()
    maven.fixture.enableInspections(Maven4RedundantParentCoordinatesInspection::class.java)
    val moduleFile = maven.createModulePom("m1", """
      <parent>
        <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>1</version>
      </parent>
      <artifactId>m1</artifactId>
""")

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>  
""")
    maven.checkHighlighting(moduleFile)
  }


  @Test
  fun testFireHighlightInMaven4() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.enableInspections(Maven4RedundantParentCoordinatesInspection::class.java)
    val moduleFile = maven.createModulePom("m1", """
     <parent>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><groupId>test</groupId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><artifactId>test</artifactId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><version>1</version></warning>
      </parent>
      <artifactId>m1</artifactId>
""")

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>  
""")
    maven.checkHighlighting(moduleFile)
  }

  @Test
  fun testDoQuickFixForParent() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.enableInspections(Maven4RedundantParentCoordinatesInspection::class.java)
    val moduleFile = maven.createModulePom("m1", """
     <parent>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><groupId>test</groupId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><artifactId>test</artifactId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><caret><version>1</version></warning>
      </parent>
      <artifactId>m1</artifactId>
""")

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>  
""")
    maven.checkHighlighting(moduleFile)

    val intention = maven.fixture.availableIntentions.singleOrNull { it.text == "Remove unnecessary tags" }
    assertNotNull(intention, "Cannot find intention")
    val expected = maven.createPomXml("""
     <parent/>
      <artifactId>m1</artifactId>
""")
    maven.fixture.launchAction(intention!!)
    maven.fixture.checkResult(expected, true)
  }

  @Test
  fun testDoQuickFixForParentWithRelativePath() = runBlocking {
    maven.assumeMaven4()
    maven.fixture.enableInspections(Maven4RedundantParentCoordinatesInspection::class.java)
    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <version>1</version>
      <packaging>pom</packaging>
      <artifactId>m1</artifactId>
""")

    val module2File = maven.createModulePom("m2", """
      <parent>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><groupId>test</groupId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><artifactId>m1</artifactId></warning>
        <warning descr="The parent coordinates are redundant and not required in Maven 4"><caret><version>1</version></warning>
        <relativePath>../m1/pom.xml</relativePath>
      </parent>
      <artifactId>m2</artifactId>
""")

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
        <module>m2</module>
      </modules>  
""")
    maven.checkHighlighting(module2File)

    val intention = maven.fixture.availableIntentions.singleOrNull { it.text == "Remove unnecessary tags" }
    assertNotNull(intention, "Cannot find intention")
    val expected = maven.createPomXml("""
      <parent>
          <relativePath>../m1/pom.xml</relativePath>
      </parent>
      <artifactId>m2</artifactId>
""")
    maven.fixture.launchAction(intention!!)
    maven.fixture.checkResult(expected, true)
  }
}
