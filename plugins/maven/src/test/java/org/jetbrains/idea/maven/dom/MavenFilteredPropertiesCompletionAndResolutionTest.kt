// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.updateProjectSubFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsDoNotInclude
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.assertNoReferences
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.assertSearchResultsInclude
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.doInlineRename
import org.jetbrains.idea.maven.fixtures.doRename
import org.jetbrains.idea.maven.fixtures.findPsiFileAndGetText
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.fixtures.getReference
import org.jetbrains.idea.maven.fixtures.resolveReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.atomic.AtomicReference

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenFilteredPropertiesCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    skipPluginResolution = false,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testBasic() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${project<caret>.version}abc")
    maven.assertResolved(f, maven.findTag("project.version"))
  }

  @Test
  fun testTestResourceProperties() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <testResources>
                        <testResource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </testResource>
                      </testResources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${project<caret>.version}abc")
    maven.assertResolved(f, maven.findTag("project.version"))
  }

  @Test
  fun testBasicAt() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc@project<caret>.version@abc")

    maven.assertResolved(f, maven.findTag("project.version"))
  }

  @Test
  fun testCorrectlyCalculatingBaseDir() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${basedir<caret>}abc")

    val baseDir = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent()) }
    maven.assertResolved(f, baseDir!!)
  }

  @Test
  fun testResolvingToNonManagedParentProperties() = runBlocking {
    maven.createProjectSubDir("res")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>parent/pom.xml</relativePath>
                       </parent>
                       <build>
                         <resources>
                           <resource>
                             <directory>res</directory>
                             <filtering>true</filtering>
                           </resource>
                         </resources>
                       </build>
                       """.trimIndent())

    val parent = maven.createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <packaging>pom</packaging>
                                           <properties>
                                             <parentProp>value</parentProp>
                                           </properties>
                                           """.trimIndent())

    maven.importProjectAsync()

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=\${parentProp<caret>}")

    maven.assertResolved(f, maven.findTag(parent, "project.properties.parentProp"))
  }

  @Test
  fun testResolvingToProfileProperties() = runBlocking {
    maven.createProjectSubDir("res")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <profileProp>value</profileProp>
                           </properties>
                         </profile>
                       </profiles>
                       <build>
                         <resources>
                           <resource>
                             <directory>res</directory>
                             <filtering>true</filtering>
                           </resource>
                         </resources>
                       </build>
                       """.trimIndent())

    maven.importProjectWithProfiles("one")

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=@profileProp<caret>@")
    val tag = maven.findTag(maven.projectPom, "project.profiles[0].properties.profileProp", MavenDomProjectModel::class.java)
    maven.assertResolved(f, tag)
  }

  @Test
  fun testDoNotResolveOutsideResources() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("foo.properties",
                                 "foo=abc\${project<caret>.version}abc")
    maven.assertNoReferences(f, MavenPropertyPsiReference::class.java)
  }

  @Test
  fun testDoNotResolveNonFilteredResources() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>false</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${project<caret>.version}abc")

    maven.assertNoReferences(f, MavenPropertyPsiReference::class.java)
  }

  @Test
  fun testUsingFilters() = runBlocking {
    val filter = maven.createProjectSubFile("filters/filter.properties", "xxx=1")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${xx<caret>x}abc")
    val psiElement = findPropertyPsiElement(filter, "xxx")!!
    maven.assertResolved(f, psiElement)
  }

  private suspend fun findPropertyPsiElement(filter: VirtualFile, propName: String): PsiElement? {
    return readAction {
      val property = MavenDomUtil.findProperty(maven.project, filter, propName)
      property?.getPsiElement()
    }
  }

  @Test
  fun testCompletionFromFilters() = runBlocking {
    maven.createProjectSubFile("filters/filter1.properties", "xxx=1")
    maven.createProjectSubFile("filters/filter2.properties", "yyy=1")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter1.properties</filter>
                        <filter>filters/filter2.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    var f = maven.createProjectSubFile("res/foo.properties", "foo=abc\${<caret>}abc")
    maven.assertCompletionVariantsInclude(f, "xxx", "yyy")

    f = maven.createProjectSubFile("res/foo2.properties", "foo=abc@<caret>@abc")
    maven.assertCompletionVariantsInclude(f, "xxx", "yyy")
  }

  @Test
  fun testSearchingFromFilters() = runBlocking {
    maven.createProjectSubFile("filters/filter.properties", "xxx=1")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 """
                                        foo=${"$"}{xxx}
                                        foo2=@xxx@
                                        """.trimIndent())
    val filter = maven.updateProjectSubFile("filters/filter.properties", "xx<caret>x=1")

    val foo = readAction { MavenDomUtil.findPropertyValue(maven.project, f, "foo") }
    assertNotNull(foo)
    val foo2 = readAction { MavenDomUtil.findPropertyValue(maven.project, f, "foo2") }
    assertNotNull(foo2)
    maven.assertSearchResultsInclude(filter, foo, foo2)
  }

  @Test
  fun testCompletionAfterOpenBrace() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${<caret>\n")

    maven.assertCompletionVariantsInclude(f, "project.version")
  }

  @Test
  fun testCompletionAfterOpenBraceInTheBeginningOfFile() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.txt",
                                 "\${<caret>\n")

    maven.assertCompletionVariantsInclude(f, "project.version")
  }

  @Test
  fun testCompletionAfterOpenBraceInTheBeginningOfPropertiesFile() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "\${<caret>\n")

    maven.assertCompletionVariantsInclude(f, "project.version")
  }

  @Test
  fun testCompletionInEmptyFile() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "<caret>\n")

    maven.assertCompletionVariantsDoNotInclude(f, "project.version")
  }

  @Test
  fun testRenaming() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <foo>value</foo>
                    </properties>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${f<caret>oo}abc")

    maven.assertResolved(f, maven.findTag("project.properties.foo"))

    maven.doRename(f, "bar")

    assertEquals(maven.createPomXml("""
                              <groupId>test</groupId>
                              <artifactId>project</artifactId>
                              <version>1</version>
                              <properties>
                                <bar>value</bar>
                              </properties>
                              <build>
                                <resources>
                                  <resource>
                                    <directory>res</directory>
                                    <filtering>true</filtering>
                                  </resource>
                                </resources>
                              </build>
                              """.trimIndent()),
                 maven.findPsiFileAndGetText(maven.projectPom))

    assertEquals("foo=abc\${bar}abc", maven.findPsiFileAndGetText(f))
  }

  @Test
  fun testRenamingFilteredProperty() = runBlocking {
    val filter = maven.createProjectSubFile("filters/filter.properties", "xxx=1")
    maven.refreshFiles(listOf(filter))
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <filters>
                        <filter>filters/filter.properties</filter>
                      </filters>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${x<caret>xx}abc")
    maven.refreshFiles(listOf(f))

    maven.assertResolved(f, findPropertyPsiElement(filter, "xxx")!!)

    maven.fixture.configureFromExistingVirtualFile(filter)

    maven.doInlineRename(f, "bar")

    assertEquals("foo=abc\${bar}abc", maven.findPsiFileAndGetText(f))
    assertEquals("bar=1", maven.findPsiFileAndGetText(filter))
  }

  @Test
  fun testCustomDelimiters() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <delimiters>
                              <delimiter>|</delimiter>
                              <delimiter>(*]</delimiter>
                            </delimiters>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo1.properties",
                                 """
                                           foo1=${'$'}{basedir}
                                           foo2=|pom.baseUri|
                                           foo3=a(ve|rsion]
                                           """.trimIndent())
    assertNotNull(maven.resolveReference(f, "basedir"))
    assertNotNull(maven.resolveReference(f, "pom.baseUri"))
    val ref = maven.getReference(f, "ve|rsion")
    assertNotNull(ref)
    assertTrue(ref!!.isSoft())
  }

  @Test
  fun testDontUseDefaultDelimiter1() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-resources-plugin</artifactId>
                          <version>2.5</version>
                          <configuration>
                            <delimiters>
                              <delimiter>|</delimiter>
                            </delimiters>
                            <useDefaultDelimiters>false</useDefaultDelimiters>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo1.properties",
                                 """
                                        foo1=${"$"}{basedir}
                                        foo2=|pom.baseUri|
                                        """.trimIndent())

    assert(maven.getReference(f, "basedir") !is MavenPropertyPsiReference)
    assertNotNull(maven.resolveReference(f, "pom.baseUri"))
  }

  @Test
  fun testDoNotAddReferenceToDelimiterDefinition() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${'$'}{zzz}</aaa>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <delimiters>
                              <delimiter>${'$'}{*}</delimiter>
                            </delimiters>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <aaa>${'$'}{<error descr="Cannot resolve symbol 'zzz'">zzz</error>}</aaa></properties>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-resources-plugin</artifactId>      <configuration>
                               <delimiters>
                                 <delimiter>${'$'}{*}</delimiter>
                               </delimiters>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testReferencesInXml() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.xml",
                                 """
                                           <root attr='${'$'}{based<caret>ir}'>
                                           </root>
                                           """.trimIndent())

    maven.refreshFiles(listOf(f))
    maven.fixture.configureFromExistingVirtualFile(f)

    val added = AtomicReference(false)

    readAction {
      val attribute = PsiTreeUtil.getParentOfType(maven.fixture.getFile().findElementAt(maven.fixture.getCaretOffset()), XmlAttribute::class.java)
      val references = attribute!!.getReferences()
      for (ref in references) {
        if (ref.resolve() is PsiDirectory) {
          added.set(true)  // Maven references was added.
          break
        }
      }
    }

    assertTrue(added.get(), "Maven filter reference was not added")
  }
}
