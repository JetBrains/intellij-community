/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference
import org.junit.Test

class MavenFilteredPropertiesCompletionAndResolutionTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testBasic() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${project<caret>.version}abc")

    assertResolved(f, findTag("project.version"))
  }

  @Test
  fun testTestResourceProperties() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${project<caret>.version}abc")

    assertResolved(f, findTag("project.version"))
  }

  @Test
  fun testBasicAt() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc@project<caret>.version@abc")

    assertResolved(f, findTag("project.version"))
  }

  @Test
  fun testCorrectlyCalculatingBaseDir() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${basedir<caret>}abc")

    val baseDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())
    assertResolved(f, baseDir!!)
  }

  @Test
  fun testResolvingToNonManagedParentProperties() = runBlocking {
    createProjectSubDir("res")

    createProjectPom("""
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

    val parent = createModulePom("parent",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <packaging>pom</packaging>
                                           <properties>
                                             <parentProp>value</parentProp>
                                           </properties>
                                           """.trimIndent())

    importProjectAsync()

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=\${parentProp<caret>}")

    assertResolved(f, findTag(parent, "project.properties.parentProp"))
  }

  @Test
  fun testResolvingToProfileProperties() = runBlocking {
    createProjectSubDir("res")

    createProjectPom("""
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

    importProjectWithProfiles("one")

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=@profileProp<caret>@")

    assertResolved(f, findTag(myProjectPom, "project.profiles[0].properties.profileProp", MavenDomProjectModel::class.java))
  }

  @Test
  fun testDoNotResolveOutsideResources() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("foo.properties",
                                 "foo=abc\${project<caret>.version}abc")
    assertNoReferences(f, MavenPropertyPsiReference::class.java)
  }

  @Test
  fun testDoNotResolveNonFilteredResources() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${project<caret>.version}abc")
    assertNoReferences(f, MavenPropertyPsiReference::class.java)
  }

  @Test
  fun testUsingFilters() = runBlocking {
    val filter = createProjectSubFile("filters/filter.properties", "xxx=1")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${xx<caret>x}abc")
    assertResolved(f, findPropertyPsiElement(filter, "xxx")!!)
  }

  private fun findPropertyPsiElement(filter: VirtualFile, propName: String): PsiElement? {
    val property = MavenDomUtil.findProperty(myProject, filter, propName)
    return property?.getPsiElement()
  }

  @Test
  fun testCompletionFromFilters() = runBlocking {
    createProjectSubFile("filters/filter1.properties", "xxx=1")
    createProjectSubFile("filters/filter2.properties", "yyy=1")

    importProjectAsync("""
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

    var f = createProjectSubFile("res/foo.properties", "foo=abc\${<caret>}abc")
    assertCompletionVariantsInclude(f, "xxx", "yyy")

    f = createProjectSubFile("res/foo2.properties", "foo=abc@<caret>@abc")
    assertCompletionVariantsInclude(f, "xxx", "yyy")
  }

  @Test
  fun testSearchingFromFilters() = runBlocking {
    createProjectSubFile("filters/filter.properties", "xxx=1")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 """
                                        foo=${"$"}{xxx}
                                        foo2=@xxx@
                                        """.trimIndent())
    val filter = createProjectSubFile("filters/filter.properties", "xx<caret>x=1")

    assertSearchResultsInclude(filter, MavenDomUtil.findPropertyValue(myProject, f, "foo"),
                               MavenDomUtil.findPropertyValue(myProject, f, "foo2"))
  }

  @Test
  fun testCompletionAfterOpenBrace() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${<caret>\n")

    assertCompletionVariantsInclude(f, "project.version")
  }

  @Test
  fun testCompletionAfterOpenBraceInTheBeginningOfFile() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.txt",
                                 "\${<caret>\n")

    assertCompletionVariantsInclude(f, "project.version")
  }

  @Test
  fun testCompletionAfterOpenBraceInTheBeginningOfPropertiesFile() = runBlocking {
    if (ignore()) return@runBlocking

    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "\${<caret>\n")

    assertCompletionVariantsInclude(f, "project.version")
  }

  @Test
  fun testCompletionInEmptyFile() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "<caret>\n")

    assertCompletionVariantsDoNotInclude(f, "project.version")
  }

  @Test
  fun testRenaming() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${f<caret>oo}abc")

    assertResolved(f, findTag("project.properties.foo"))

    doRename(f, "bar")

    assertEquals(createPomXml("""
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
                 findPsiFile(myProjectPom).getText())

    assertEquals("foo=abc\${bar}abc", findPsiFile(f).getText())
  }

  @Test
  fun testRenamingFilteredProperty() = runBlocking {
    val filter = createProjectSubFile("filters/filter.properties", "xxx=1")
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${x<caret>xx}abc")
    assertResolved(f, findPropertyPsiElement(filter, "xxx")!!)
    myFixture.configureFromExistingVirtualFile(filter)
    doInlineRename(f, "bar")

    assertEquals("foo=abc\${bar}abc", findPsiFile(f).getText())
    assertEquals("bar=1", findPsiFile(filter).getText())
  }

  @Test
  fun testCustomDelimiters() = runBlocking {
    createProjectSubDir("res")

    importProjectAndExpectResourcePluginIndexed("""
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
                              <delimiter>(*]</delimiter>
                            </delimiters>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val f = createProjectSubFile("res/foo1.properties",
                                 """
                                           foo1=${'$'}{basedir}
                                           foo2=|pom.baseUri|
                                           foo3=a(ve|rsion]
                                           """.trimIndent())

    assertNotNull(resolveReference(f, "basedir"))
    assertNotNull(resolveReference(f, "pom.baseUri"))
    val ref = getReference(f, "ve|rsion")
    assertNotNull(ref)
    assertTrue(ref!!.isSoft())
  }

  @Test
  fun testDontUseDefaultDelimiter1() = runBlocking {
    createProjectSubDir("res")

    importProjectAndExpectResourcePluginIndexed("""
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

    val f = createProjectSubFile("res/foo1.properties",
                                 """
                                        foo1=${"$"}{basedir}
                                        foo2=|pom.baseUri|
                                        """.trimIndent())

    assert(getReference(f, "basedir") !is MavenPropertyPsiReference)
    assertNotNull(resolveReference(f, "pom.baseUri"))
  }

  @Test
  fun testDoNotAddReferenceToDelimiterDefinition() = runBlocking {
    importProjectAndExpectResourcePluginIndexed("""
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

    createProjectPom("""
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

    checkHighlighting()
  }

  @Test
  fun testReferencesInXml() = runBlocking {
    createProjectSubDir("res")

    importProjectAsync("""
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

    val f = createProjectSubFile("res/foo.xml",
                                 """
                                           <root attr='${'$'}{based<caret>ir}'>
                                           </root>
                                           """.trimIndent())

    myFixture.configureFromExistingVirtualFile(f)

    val attribute = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), XmlAttribute::class.java)

    val references = attribute!!.getReferences()

    for (ref in references) {
      if (ref.resolve() is PsiDirectory) {
        return@runBlocking  // Maven references was added.
      }
    }

    fail("Maven filter reference was not added")
  }

  private fun importProjectAndExpectResourcePluginIndexed(@Language(value = "XML", prefix = "<project>",
                                                                    suffix = "</project>") xml: String) = runBlocking {
    runAndExpectPluginIndexEvents(setOf("maven-resources-plugin")) {
      importProjectAsync(xml)
    }
  }
}
