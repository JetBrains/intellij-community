package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantGroupIdInspection
import org.junit.Test

class MavenRedundantGroupIdTest : MavenDomTestCase() {
  override fun runInDispatchThread() = true

  override fun setUp() {
    super.setUp()

    fixture.enableInspections(MavenRedundantGroupIdInspection::class.java)
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
                       <version>1.0</version>
                         
                       <parent>
                         <groupId>my.group</groupId>
                         <artifactId>parent</artifactId>
                         <version>1.0</version>
                       </parent>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlighting3() = runBlocking {
    createProjectPom("""
                       <warning><groupId>my.group</groupId></warning>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <parent>
                         <groupId>my.group</groupId>
                         <artifactId>parent</artifactId>
                         <version>1.0</version>
                       </parent>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testQuickFix() = runBlocking {
    createProjectPom("""
                       <artifactId>childA</artifactId>
                       <groupId>mavenParen<caret>t</groupId>
                       <version>1.0</version>
                         
                       <parent>
                         <groupId>mavenParent</groupId>
                         <artifactId>childA</artifactId>
                         <version>1.0</version>
                       </parent>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(myProjectPom)
    fixture.doHighlighting()

    for (intention in fixture.availableIntentions) {
      if (intention.text.startsWith("Remove ") && intention.text.contains("<groupId>")) {
        fixture.launchAction(intention)
        break
      }
    }


    //doPostponedFormatting(myProject)
    PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting()

    fixture.checkResult(createPomXml("""
                                                       <artifactId>childA</artifactId>
                                                           <version>1.0</version>
                                                         
                                                       <parent>
                                                         <groupId>mavenParent</groupId>
                                                         <artifactId>childA</artifactId>
                                                         <version>1.0</version>
                                                       </parent>
                                                       """.trimIndent()))
  }
}
