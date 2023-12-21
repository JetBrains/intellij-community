package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenDependencySmartCompletionTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           ju<caret>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariantsInclude(projectPom, RENDERING_TEXT, "junit:junit")
  }

  @Test
  fun testInsertDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>juni<caret></dependency>
                       </dependencies>
                       """.trimIndent())

    configTest(projectPom)
    val elements = fixture.completeBasic()
    assertCompletionVariants(fixture, RENDERING_TEXT, "junit:junit")
    UsefulTestCase.assertSize(1, elements)

    fixture.type('\n')


    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version><caret></version>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testInsertManagedDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>junit:<caret></dependency>
                       </dependencies>
                       """.trimIndent())

    configTest(projectPom)
    fixture.complete(CompletionType.BASIC)
    assertCompletionVariants(fixture, RENDERING_TEXT, "junit:junit")
    fixture.type('\n')

    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version>4.0</version>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testInsertManagedDependencyWithTypeAndClassifier() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitClassifier>sources</junitClassifier>
                         <junitType>test-jar</junitType>
                       </properties>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                             <type>${'$'}{junitType}</type>
                             <classifier>${'$'}{junitClassifier}</classifier>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>junit:<caret></dependency>
                       </dependencies>
                       """.trimIndent())

    configTest(projectPom)

    val elements = fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)

    fixture.type('\n')


    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <junitClassifier>sources</junitClassifier>
                                           <junitType>test-jar</junitType>
                                         </properties>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version>4.0</version>
                                               <type>${'$'}{junitType}</type>
                                               <classifier>${'$'}{junitClassifier}</classifier>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <type>${'$'}{junitType}</type>
                                               <classifier>${'$'}{junitClassifier}</classifier>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testCompletionArtifactIdThenVersion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>juni<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)

    var elements = fixture.completeBasic()
    assertTrue(elements.size > 0)
    assertEquals("junit:junit:3.8.1", elements[0].getLookupString())

    fixture.type('\n')

    elements = fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)
    fixture.type('\n')

    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version><caret></version>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))

    assertTrue(
      fixture.getLookupElementStrings()!!.containsAll(mutableListOf("3.8.1", "4.0")))
  }

  @Test
  fun testCompletionArtifactIdThenGroupIdThenInsertVersion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>intellijartif<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)

    val elements = fixture.completeBasic()

    assertCompletionVariants(fixture, RENDERING_TEXT, "intellijartifactanother", "intellijartifact")

    fixture.type('\n')

    assertCompletionVariants(fixture, RENDERING_TEXT, "org.intellijgroup")

    fixture.type("\n")

    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>org.intellijgroup</groupId>
                                               <artifactId>intellijartifact</artifactId>
                                               <version>1.0</version>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testCompletionArtifactIdNonExactmatch() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>intellijmavent<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)
    val elements = fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)

    fixture.type('\n')

    assertCompletionVariants(fixture, RENDERING_TEXT, "org.example")
  }

  @Test
  fun testCompletionArtifactIdInsideManagedDependency() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencyManagement>
                           <dependencies>
                               <dependency>
                                   <artifactId>intellijmavente<caret></artifactId>
                               </dependency>
                           </dependencies>
                       </dependencyManagement>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)

    val elements = fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)
    fixture.type('\n')

    assertCompletionVariants(fixture, RENDERING_TEXT, "org.example")

    fixture.type('\n')

    assertCompletionVariants(fixture, RENDERING_TEXT, "1.0", "2.0")

    fixture.type('\n')

    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencyManagement>
                                             <dependencies>
                                                 <dependency>
                                                     <groupId>org.example</groupId>
                                                     <artifactId>intellijmaventest</artifactId>
                                                     <version>2.0</version>
                                                 </dependency>
                                             </dependencies>
                                         </dependencyManagement>
                                         """.trimIndent()))
  }

  @Test
  fun testCompletionArtifactIdWithManagedDependency() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>org.intellijgroup</groupId>
                            <artifactId>intellijartifact</artifactId>
                            <version>1.0</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                         <dependencyManagement>
                           <dependencies>
                             <dependency>
                               <groupId>org.intellijgroup</groupId>
                               <artifactId>intellijartifactanother</artifactId>
                               <version>1.0</version>
                             </dependency>
                           </dependencies>
                         </dependencyManagement>
                       <dependencies>
                         <dependency>
                           <artifactId>intellijartifactan<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)

    var elements = fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements!!)
    fixture.type('\n')

    elements = fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)
    fixture.type('\n')

    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                           <dependencyManagement>
                                             <dependencies>
                                               <dependency>
                                                 <groupId>org.intellijgroup</groupId>
                                                 <artifactId>intellijartifactanother</artifactId>
                                                 <version>1.0</version>
                                               </dependency>
                                             </dependencies>
                                           </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>org.intellijgroup</groupId>
                                               <artifactId>intellijartifactanother</artifactId>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()
    ))
  }

  @Test
  fun testCompletionGroupIdWithManagedDependencyWithTypeAndClassifier() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                    <properties>
                      <ioClassifier>ccc</ioClassifier>  <ioType>ttt</ioType></properties>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>commons-io</groupId>
                          <artifactId>commons-io</artifactId>
                          <classifier>${'$'}{ioClassifier}</classifier>
                          <type>${'$'}{ioType}</type>
                          <version>2.4</version>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>commons-io</groupId>
                             <artifactId>commons-io</artifactId>
                             <classifier>${'$'}{ioClassifier}</classifier>
                             <type>${'$'}{ioType}</type>
                             <version>2.4</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>
                             <groupId>commons-i<caret></groupId>
                             <artifactId>commons-io</artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    fixture.configureFromExistingVirtualFile(projectPom)

    val elements = fixture.complete(CompletionType.BASIC)
    UsefulTestCase.assertSize(1, elements)
    fixture.type('\n')

    fixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                               <classifier>${'$'}{ioClassifier}</classifier>
                                               <type>${'$'}{ioType}</type>
                                               <version>2.4</version>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                               <type>${'$'}{ioType}</type>
                                               <classifier>${'$'}{ioClassifier}</classifier>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()
    ))
  }
}
