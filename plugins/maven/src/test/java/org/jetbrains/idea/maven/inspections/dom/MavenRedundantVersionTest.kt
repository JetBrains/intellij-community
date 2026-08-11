package org.jetbrains.idea.maven.inspections.dom

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantVersionInspection
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenRedundantVersionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.fixture.enableInspections(MavenRedundantVersionInspection::class.java)

    runBlocking {
      maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>test</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    }
  }

  @Test
  fun testHighlighting1() = runBlocking {
    maven.createProjectPom("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlighting2() = runBlocking {
    maven.createProjectPom("""
                       <groupId>childGroupId</groupId>
                       <artifactId>childA</artifactId>
                       <version>2.0</version>
                         
                       <parent>
                         <groupId><error descr="Project 'my.group:parent:1.0' not found">my.group</error></groupId>
                        <artifactId><error descr="Project 'my.group:parent:1.0' not found">parent</error></artifactId>
                        <version><error descr="Project 'my.group:parent:1.0' not found">1.0</error></version>
                       </parent>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlighting3() = runBlocking {
    maven.createProjectPom("""
                       <artifactId>childA</artifactId>
                       <warning descr="Definition of version is redundant, because it's inherited from the parent"><version>1.0</version></warning>
                         
                       <parent>
                         <groupId><error descr="Project 'my.group:parent:1.0' not found">my.group</error></groupId>
                         <artifactId><error descr="Project 'my.group:parent:1.0' not found">parent</error></artifactId>
                         <version><error descr="Project 'my.group:parent:1.0' not found">1.0</error></version>
                       </parent>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testQuickFix() = runBlocking {
    maven.createProjectPom("""
                       <artifactId>childA</artifactId>
                       <groupId>mavenParent</groupId>
                       <version<caret>>1.0</version>
                       <parent>
                         <groupId>mavenParent</groupId>
                         <artifactId>mavenParent</artifactId>
                         <version>1.0</version>
                       </parent>
                       """.trimIndent())

    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)
    maven.fixture.doHighlighting()

    val intention =  maven.fixture.availableIntentions.singleOrNull{it.text.startsWith("Remove ") && it.text.contains("version")}
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)

    maven.fixture.checkResult(maven.createPomXml("""
                                                       <artifactId>childA</artifactId>
                                                       <groupId>mavenParent</groupId>
                                                           <parent>
                                                         <groupId>mavenParent</groupId>
                                                         <artifactId>mavenParent</artifactId>
                                                         <version>1.0</version>
                                                       </parent>
                                                       """.trimIndent()))
  }
}
