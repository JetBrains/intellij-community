package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantVersionInspection
import org.junit.Test

class MavenRedundantVersionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()

    fixture.enableInspections(MavenRedundantVersionInspection::class.java)

    runBlocking {
      importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>test</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    }
  }

  @Test
  fun testHighlighting1() = runBlocking {
    createProjectPom("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlighting2() = runBlocking {
    createProjectPom("""
                       <groupId>childGroupId</groupId>
                       <artifactId>childA</artifactId>
                       <version>2.0</version>
                         
                       <parent>
                         <groupId><error descr="Project 'my.group:parent:1.0' not found">my.group</error></groupId>
                        <artifactId><error descr="Project 'my.group:parent:1.0' not found">parent</error></artifactId>
                        <version><error descr="Project 'my.group:parent:1.0' not found">1.0</error></version>
                       </parent>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlighting3() = runBlocking {
    createProjectPom("""
                       <artifactId>childA</artifactId>
                       <warning descr="Definition of version is redundant, because it's inherited from the parent"><version>1.0</version></warning>
                         
                       <parent>
                         <groupId><error descr="Project 'my.group:parent:1.0' not found">my.group</error></groupId>
                         <artifactId><error descr="Project 'my.group:parent:1.0' not found">parent</error></artifactId>
                         <version><error descr="Project 'my.group:parent:1.0' not found">1.0</error></version>
                       </parent>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testQuickFix() = runBlocking {
    createProjectPom("""
                       <artifactId>childA</artifactId>
                       <groupId>mavenParent</groupId>
                       <version<caret>>1.0</version>
                       <parent>
                         <groupId>mavenParent</groupId>
                         <artifactId>mavenParent</artifactId>
                         <version>1.0</version>
                       </parent>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)
    fixture.doHighlighting()

    val intention =  fixture.availableIntentions.singleOrNull{it.text.startsWith("Remove ") && it.text.contains("version")}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult(createPomXml("""
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
