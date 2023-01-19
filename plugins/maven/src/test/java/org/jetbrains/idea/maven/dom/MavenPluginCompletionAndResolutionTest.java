// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.junit.Test;

import java.util.List;

public class MavenPluginCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir.toPath(), myProject, "plugins");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
  }

  @Test
  public void testGroupIdCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId><caret></groupId>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "intellij.test", "test", "org.apache.maven.plugins", "org.codehaus.mojo",
                             "org.codehaus.plexus");
  }

  @Test 
  public void testArtifactIdCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId><caret></artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "maven-site-plugin", "maven-eclipse-plugin", "maven-war-plugin",
                             "maven-resources-plugin", "maven-surefire-plugin", "maven-jar-plugin", "maven-clean-plugin",
                             "maven-install-plugin", "maven-compiler-plugin", "maven-deploy-plugin");
  }

  @Test 
  public void testVersionCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <version><caret></version>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "2.0.2", "3.1");
  }

  @Test 
  public void testArtifactWithoutGroupCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret></artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT,
                             "project",
                             "maven-clean-plugin",
                             "maven-jar-plugin",
                             "maven-war-plugin",
                             "maven-deploy-plugin",
                             "maven-resources-plugin",
                             "maven-eclipse-plugin",
                             "maven-install-plugin",
                             "maven-compiler-plugin",
                             "maven-site-plugin",
                             "maven-surefire-plugin",
                             "build-helper-maven-plugin");
  }

  @Test 
  public void testCompletionInsideTag() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin><caret></plugin>
                         </plugins>
                       </build>
                       """);

    assertDependencyCompletionVariantsInclude(myProjectPom,
                                    "org.apache.maven.plugins:maven-clean-plugin:2.5",
                                    "org.apache.maven.plugins:maven-compiler-plugin",
                                    "org.apache.maven.plugins:maven-deploy-plugin:2.7",
                                    "org.apache.maven.plugins:maven-eclipse-plugin:2.4",
                                    "org.apache.maven.plugins:maven-install-plugin:2.4",
                                    "org.apache.maven.plugins:maven-jar-plugin:2.4",
                                    "org.apache.maven.plugins:maven-resources-plugin:2.6",
                                    "org.apache.maven.plugins:maven-site-plugin:3.3",
                                    "org.apache.maven.plugins:maven-surefire-plugin",
                                    "org.apache.maven.plugins:maven-war-plugin:2.1-alpha-1",
                                    "org.codehaus.mojo:build-helper-maven-plugin:1.0",
                                    "test:project:1");
  }

  @Test 
  public void testVersionWithoutGroupCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <version><caret></version>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "2.0.2", "3.1");
  }

  @Test 
  public void testResolvingPlugins() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    String pluginPath = "plugins/org/apache/maven/plugins/maven-compiler-plugin/3.1/maven-compiler-plugin-3.1.pom";
    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath(pluginPath);
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  @Test 
  public void testResolvingAbsentPlugins() {
    removeFromLocalRepository("org/apache/maven/plugins/maven-compiler-plugin");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><caret>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    ref.resolve(); // shouldn't throw;
  }

  @Test 
  public void testDoNotHighlightAbsentGroupIdAndVersion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                           </plugin>
                         </plugins>
                       </build>
                       """);
    checkHighlighting();
  }

  @Test 
  public void testHighlightingAbsentArtifactId() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <<error descr="'artifactId' child tag should be defined">plugin</error>>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testBasicConfigurationCompletion() {
    putCaretInConfigurationSection();
    assertCompletionVariantsInclude(myProjectPom, "source", "target");
  }

  @Test 
  public void testIncludingConfigurationParametersFromAllTheMojos() {
    putCaretInConfigurationSection();
    assertCompletionVariantsInclude(myProjectPom, "excludes", "testExcludes");
  }

  private void putCaretInConfigurationSection() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);
  }

  @Test 
  public void testNoParametersForUnknownPlugin() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>unknown-plugin</artifactId>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom);
  }

  @Test 
  public void testNoParametersIfNothingIsSpecified() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom);
  }

  @Test 
  public void testResolvingParamaters() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <<caret>includes></includes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof XmlTag);
    assertEquals("parameter", ((XmlTag)resolved).getName());
    assertEquals("includes", ((XmlTag)resolved).findFirstSubTag("name").getValue().getText());
  }

  @Test 
  public void testResolvingInnerParamatersIntoOuter() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <includes>
                                 <<caret>include></include        </includes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof XmlTag);
    assertEquals("parameter", ((XmlTag)resolved).getName());
    assertEquals("includes", ((XmlTag)resolved).findFirstSubTag("name").getValue().getText());
  }

  @Test 
  public void testGoalsCompletionAndHighlighting() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "help", "compile", "testCompile");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><error>xxx</error></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testDontHighlightGoalsForUnresolvedPlugin() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                   <goal><error>unknownGoal</error></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                           <plugin>
                             <artifactId><error>unresolved-plugin</error></artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                   <goal>unknownGoal</goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>""");

    checkHighlighting();
  }

  @Test 
  public void testGoalsCompletionAndHighlightingInPluginManagement() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <pluginManagement>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                         </pluginManagement>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "help", "compile", "testCompile");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <pluginManagement>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><error>xxx</error></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                         </pluginManagement>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testGoalsResolution() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret>compile</goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof XmlTag);
    assertEquals("mojo", ((XmlTag)resolved).getName());
    assertEquals("compile", ((XmlTag)resolved).findFirstSubTag("goal").getValue().getText());
  }


  @Test 
  public void testMavenDependencyReferenceProvider() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                               <plugins>
                                   <plugin>
                                       <artifactId>maven-invoker-plugin</artifactId>
                                       <version>3.2.1</version>
                                       <executions>
                                           <execution>
                                               <id>pre-integration-tests</id>
                                               <goals>
                                                   <goal>install</goal>
                                               </goals>
                                               <configuration>
                                                   <extraArtifacts>
                                                       <extraArtifact>junit:<caret>junit:4.8</extraArtifact>
                                                   </extraArtifacts>
                                               </configuration>
                                           </execution>
                                       </executions>
                                   </plugin>
                               </plugins>
                           </build>
                       """);

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

  }


  @Test 
  public void testGoalsCompletionAndResolutionForUnknownPlugin() throws Throwable {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>xxx</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret></goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom);

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>xxx</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal><caret>compile</goal>
                                 </goals>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertUnresolved(myProjectPom);
  }

  @Test 
  public void testPhaseCompletionAndHighlighting() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <phase><caret></phase>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariantsInclude(myProjectPom, "clean", "compile", "package");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <phase><error>xxx</error></phase>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testNoExecutionParametersIfNoGoalNorIdAreSpecified() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom);
  }

  @Test 
  public void testExecutionParametersForSpecificGoal() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                 </goals>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    List<String> variants = getCompletionVariants(myProjectPom);
    assertTrue(variants.toString(), variants.contains("excludes"));
    assertFalse(variants.toString(), variants.contains("testExcludes"));
  }

  @Test 
  public void testExecutionParametersForDefaultGoalExecution() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <id>default-compile</id>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    List<String> variants = getCompletionVariants(myProjectPom);
    assertTrue(variants.toString(), variants.contains("excludes"));
    assertFalse(variants.toString(), variants.contains("testExcludes"));
  }

  @Test 
  public void testExecutionParametersForSeveralSpecificGoals() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <executions>
                               <execution>
                                 <goals>
                                   <goal>compile</goal>
                                   <goal>testCompile</goal>
                                 </goals>
                                 <configuration>
                                   <<caret>
                                 </configuration>
                               </execution>
                             </executions>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariantsInclude(myProjectPom, "excludes", "testExcludes");
  }

  @Test 
  public void testAliasCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-war-plugin</artifactId>
                             <configuration>
                               <<caret>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariantsInclude(myProjectPom, "warSourceExcludes", "excludes");
  }

  @Test 
  public void testListElementsCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <excludes>
                                 <exclude></exclude>
                                 <<caret>
                               </excludes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "exclude");
  }

  @Test 
  public void testListElementWhatHasUnpluralizedNameCompletion() {
    // NPE test - StringUtil.unpluralize returns null.

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-eclipse-plugin</artifactId>
                             <configuration>
                               <additionalConfig>
                                 <<caret>
                               </additionalConfig>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "additionalConfig", "config");
  }

  @Test 
  public void testDoNotHighlightUnknownElementsUnderLists() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <excludes>
                                 <foo>foo</foo>
                               </excludes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testArrayElementsCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-war-plugin</artifactId>
                             <configuration>
                               <webResources>
                                 <webResource></webResource>
                                 <<caret>
                               </webResources>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "resource", "webResource");
  }

  @Test 
  public void testCompletionInCustomObjects() {
    if (ignore()) return;

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-war-plugin</artifactId>
                             <configuration>
                               <webResources>
                                 <webResource>
                                   <<caret>
                                 </webResource>
                               </webResources>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom);
  }

  @Test 
  public void testDocumentationForParameter() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <s<caret>ource></source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertDocumentation("Type: <b>java.lang.String</b><br>Default Value: <b>1.5</b><br>Expression: <b>${maven.compiler.source}</b><br><br><i>The -source argument for the Java compiler.</i>");
  }

  @Test 
  public void testDoNotCompleteNorHighlightNonPluginConfiguration() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <ciManagement>
                         <system>foo</system>
                         <notifiers>
                           <notifier>
                             <type>mail</type>
                             <configuration>
                               <address>foo@bar.com</address>
                             </configuration>
                           </notifier>
                         </notifiers>
                       </ciManagement>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testDoNotHighlightInnerParameters() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <source>
                                 <foo>*.java</foo>
                               </source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testDoNotHighlightRequiredParametersWithDefaultValues() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-surefire-plugin</artifactId>
                             <version>2.4.3</version>
                             <configuration>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting(); // surefire plugin has several required parameters with default values.
  }

  @Test 
  public void testDoNotHighlightInnerParameterAttributes() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <includes value1='1'>
                                 <include value2='2'/>
                               </includes>
                               <source value3='3'>
                                 <child value4='4'/>
                               </source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testDoNotCompleteParameterAttributes() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <source <caret>/>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom, "combine.children", "combine.self");
  }

  @Test 
  public void testWorksWithPropertiesInPluginId() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <plugin.groupId>org.apache.maven.plugins</plugin.groupId>
                         <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>
                         <plugin.version>2.0.2</plugin.version>
                       </properties>
                       """);
    importProject(); // let us recognize the properties first

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <plugin.groupId>org.apache.maven.plugins</plugin.groupId>
                         <plugin.artifactId>maven-compiler-plugin</plugin.artifactId>
                         <plugin.version>2.0.2</plugin.version>
                       </properties>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>${plugin.groupId}</groupId>
                             <artifactId>${plugin.artifactId}</artifactId>
                             <version>${plugin.version}</version>
                             <configuration>
                               <source></source>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testDoNotHighlightPropertiesForUnknownPlugins() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId><error>foo.bar</error></artifactId>
                             <configuration>
                               <prop>
                                 <value>foo</value>
                               </prop>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testTellNobodyThatIdeaIsRulezzz() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <artifactId>maven-compiler-plugin</artifactId>
                             <configuration>
                               <includes>
                                 <bar a<caret> />
                               </includes>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);

    assertCompletionVariants(myProjectPom);
  }
}
