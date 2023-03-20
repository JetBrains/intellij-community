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
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.properties.IProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference;
import org.junit.Test;

public class MavenFilteredPropertiesCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Test
  public void testBasic() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${project<caret>.version}abc");

    assertResolved(f, findTag("project.version"));
  }

  @Test
  public void testTestResourceProperties() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${project<caret>.version}abc");

    assertResolved(f, findTag("project.version"));
  }

  @Test
  public void testBasicAt() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc@project<caret>.version@abc");

    assertResolved(f, findTag("project.version"));
  }

  @Test
  public void testCorrectlyCalculatingBaseDir() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${basedir<caret>}abc");

    PsiDirectory baseDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent());
    assertResolved(f, baseDir);
  }

  @Test
  public void testResolvingToNonManagedParentProperties() throws Exception {
    createProjectSubDir("res");

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
                       """);

    VirtualFile parent = createModulePom("parent",
                                         """
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                           <packaging>pom</packaging>
                                           <properties>
                                             <parentProp>value</parentProp>
                                           </properties>
                                           """);

    importProject();

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=${parentProp<caret>}");

    assertResolved(f, findTag(parent, "project.properties.parentProp"));
  }

  @Test
  public void testResolvingToProfileProperties() throws Exception {
    createProjectSubDir("res");

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
                       """);

    importProjectWithProfiles("one");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=@profileProp<caret>@");

    assertResolved(f, findTag(myProjectPom, "project.profiles[0].properties.profileProp", MavenDomProjectModel.class));
  }

  @Test
  public void testDoNotResolveOutsideResources() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("foo.properties",
                                         "foo=abc${project<caret>.version}abc");
    assertNoReferences(f, MavenPropertyPsiReference.class);
  }

  @Test
  public void testDoNotResolveNonFilteredResources() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${project<caret>.version}abc");
    assertNoReferences(f, MavenPropertyPsiReference.class);
  }

  @Test
  public void testUsingFilters() throws Exception {
    VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${xx<caret>x}abc");
    assertResolved(f, findPropertyPsiElement(filter, "xxx"));
  }

  @Nullable
  private PsiElement findPropertyPsiElement(final VirtualFile filter, final String propName) {
    final IProperty property = MavenDomUtil.findProperty(myProject, filter, propName);
    return property != null ? property.getPsiElement() : null;
  }

  @Test
  public void testCompletionFromFilters() throws Exception {
    createProjectSubFile("filters/filter1.properties", "xxx=1");
    createProjectSubFile("filters/filter2.properties", "yyy=1");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties", "foo=abc${<caret>}abc");
    assertCompletionVariantsInclude(f, "xxx", "yyy");

    f = createProjectSubFile("res/foo2.properties", "foo=abc@<caret>@abc");
    assertCompletionVariantsInclude(f, "xxx", "yyy");
  }

  @Test
  public void testSearchingFromFilters() throws Exception {
    createProjectSubFile("filters/filter.properties", "xxx=1");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=${xxx}\n" +
                                         "foo2=@xxx@");
    VirtualFile filter = createProjectSubFile("filters/filter.properties", "xx<caret>x=1");

    assertSearchResultsInclude(filter, MavenDomUtil.findPropertyValue(myProject, f, "foo"), MavenDomUtil.findPropertyValue(myProject, f, "foo2"));
  }

  @Test
  public void testCompletionAfterOpenBrace() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${<caret>\n");

    assertCompletionVariantsInclude(f, "project.version");
  }

  @Test
  public void testCompletionAfterOpenBraceInTheBeginningOfFile() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.txt",
                                         "${<caret>\n");

    assertCompletionVariantsInclude(f, "project.version");
  }

  @Test
  public void testCompletionAfterOpenBraceInTheBeginningOfPropertiesFile() throws Exception {
    if (ignore()) return;

    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "${<caret>\n");

    assertCompletionVariantsInclude(f, "project.version");
  }

  @Test
  public void testCompletionInEmptyFile() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "<caret>\n");

    assertCompletionVariantsDoNotInclude(f, "project.version");
  }

  @Test
  public void testRenaming() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${f<caret>oo}abc");

    assertResolved(f, findTag("project.properties.foo"));

    doRename(f, "bar");

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
                                """),
                 findPsiFile(myProjectPom).getText());

    assertEquals("foo=abc${bar}abc", findPsiFile(f).getText());
  }

  @Test
  public void testRenamingFilteredProperty() throws Exception {
    VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${x<caret>xx}abc");
    assertResolved(f, findPropertyPsiElement(filter, "xxx"));
    myFixture.configureFromExistingVirtualFile(filter);
    doInlineRename(f, "bar");

    assertEquals("foo=abc${bar}abc", findPsiFile(f).getText());
    assertEquals("bar=1", findPsiFile(filter).getText());
  }

  @Test
  public void testCustomDelimiters() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo1.properties",
                                         """
                                           foo1=${basedir}
                                           foo2=|pom.baseUri|
                                           foo3=a(ve|rsion]""");

    assertNotNull(resolveReference(f, "basedir"));
    assertNotNull(resolveReference(f, "pom.baseUri"));
    PsiReference ref = getReference(f, "ve|rsion");
    assertNotNull(ref);
    assertTrue(ref.isSoft());
  }

  @Test
  public void testDontUseDefaultDelimiter1() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo1.properties",
                                         "foo1=${basedir}\n" +
                                         "foo2=|pom.baseUri|");

    assert !(getReference(f, "basedir") instanceof MavenPropertyPsiReference);
    assertNotNull(resolveReference(f, "pom.baseUri"));
  }

  @Test
  public void testDoNotAddReferenceToDelimiterDefinition() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${zzz}</aaa>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-resources-plugin</artifactId>
                          <configuration>
                            <delimiters>
                              <delimiter>${*}</delimiter>
                            </delimiters>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>""");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <aaa>${<error descr="Cannot resolve symbol 'zzz'">zzz</error>}</aaa></properties>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-resources-plugin</artifactId>      <configuration>
                               <delimiters>
                                 <delimiter>${*}</delimiter>
                               </delimiters>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>""");

    checkHighlighting();
  }

  @Test
  public void testReferencesInXml() throws Exception {
    createProjectSubDir("res");

    importProject("""
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
                    """);

    VirtualFile f = createProjectSubFile("res/foo.xml",
                                         """
                                           <root attr='${based<caret>ir}'>
                                           </root>
                                           """);

    myFixture.configureFromExistingVirtualFile(f);

    XmlAttribute attribute = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), XmlAttribute.class);

    PsiReference[] references = attribute.getReferences();

    for (PsiReference ref : references) {
      if (ref.resolve() instanceof PsiDirectory) {
        return; // Maven references was added.
      }
    }

    fail("Maven filter reference was not added");
  }
}
