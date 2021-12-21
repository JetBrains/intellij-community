// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.properties.IProperty;
import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;
import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.importing.FilesList;
import org.jetbrains.idea.maven.project.importing.MavenImportFlow;
import org.jetbrains.idea.maven.project.importing.MavenInitialImportContext;
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenPropertyCompletionAndResolutionTest extends MavenDomTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Test
  public void testResolutionToProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testResolutionToProjectAt() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>@<caret>project.version@</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testCorrectlyCalculatingTextRangeWithLeadingWhitespaces() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>     ${<caret>project.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testBuiltInBasedirProperty() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>basedir}</name>");

    PsiDirectory baseDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent());
    assertResolved(myProjectPom, baseDir);

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.basedir}</name>");

    assertResolved(myProjectPom, baseDir);

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>pom.basedir}</name>");

    assertResolved(myProjectPom, baseDir);
  }

  @Test
  public void testResolutionWithSeveralProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.artifactId}-${project.version}</name>");

    assertResolved(myProjectPom, findTag("project.artifactId"));

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${project.artifactId}-${<caret>project.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testResolvingFromPropertiesSection() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>${<caret>project.version}</foo>" +
                     "</properties>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testResolvingFromPropertiesSectionAt() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>@<caret>project.version@</foo>" +
                     "</properties>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testResolutionToUnknownProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.bar}</name>");

    assertUnresolved(myProjectPom);
  }

  @Test
  public void testResolutionToAbsentProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.description}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

  @Test
  public void testResolutionToAbsentPomProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>pom.description}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

  @Test
  public void testResolutionToAbsentUnclassifiedProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>description}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

  @Test
  public void testResolutionToPomProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>pom.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testResolutionToUnclassifiedProperty() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  @Test
  public void testResolutionToDerivedCoordinatesFromProjectParent() throws Exception {
    createProjectPom("<artifactId>project</artifactId>" +

                     "<parent>" +
                     "  <groupId>test</groupId" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +

                     "<name>${<caret>project.version}</name>");

    assertResolved(myProjectPom, findTag("project.parent.version"));
  }

  @Test
  public void testResolutionToProjectParent() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +

                     "<name>${<caret>project.parent.version}</name>");

    assertResolved(myProjectPom, findTag("project.parent.version"));
  }

  @Test
  public void testResolutionToInheritedModelPropertiesForManagedParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     " <directory>dir</directory>" +
                     "</build>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>" +

                                        "<name>${project.build.directory}</name>");
    importProjects(myProjectPom, child);

    createModulePom("child",
                    "<groupId>test</groupId>" +
                    "<artifactId>child</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<name>${<caret>project.build.directory}</name>");

    assertResolved(child, findTag(myProjectPom, "project.build.directory"));
  }

  @Test
  public void testResolutionToInheritedModelPropertiesForRelativeParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>project.build.directory}</name>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<build>" +
                                         "  <directory>dir</directory>" +
                                         "</build>");

    assertResolved(myProjectPom, findTag(parent, "project.build.directory"));
  }

  @Test
  public void testResolutionToInheritedPropertiesForNonManagedParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>foo}</name>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<properties>" +
                                         "  <foo>value</foo>" +
                                         "</properties>");

    assertResolved(myProjectPom, findTag(parent, "project.properties.foo"));
  }

  @Test
  public void testResolutionToInheritedSuperPomProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.build.finalName}</name>");

    VirtualFile effectiveSuperPom = getMavenGeneralSettings().getEffectiveSuperPom();
    assertNotNull(effectiveSuperPom);
    assertResolved(myProjectPom, findTag(effectiveSuperPom, "project.build.finalName"));
  }

  @Test
  public void testHandleResolutionRecursion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>project.description}</name>");

    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "  <relativePath>../pom.xml</version>" +
                    "</parent>");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.name"));
  }

  @Test
  public void testResolutionFromProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>" +

                     "<name>${<caret>foo}</name>");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"));
  }

  @Test
  public void testResolutionWithProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<name>${<caret>foo}</name>");

    readWithProfiles("two");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"));
  }

  @Test
  public void testResolutionToPropertyDefinedWithinProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "    <build>" +
                     "      <finalName>${<caret>foo}</finalName>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>");

    readWithProfiles("one");
    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"));
  }

  @Test
  public void testResolutionToPropertyDefinedOutsideProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <build>" +
                     "      <finalName>${<caret>foo}</finalName>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>");

    readWithProfiles("one");
    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"));
  }

  @Test
  public void testResolutionWithDefaultProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<name>${<caret>foo}</name>");

    readProjects();

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"));
  }

  @Test
  public void testResolutionWithTriggeredProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <jdk>[1.5,)</jdk>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<name>${<caret>foo}</name>");

    readProjects();

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"));
  }

  @Test
  public void testResolvingToProfilesBeforeModelsProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <foo>value</foo>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<name>${<caret>foo}</name>");

    readWithProfiles("one");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[0].properties.foo"));
  }

  @Test
  public void testResolvingPropertiesInSettingsXml() throws Exception {
    VirtualFile profiles = updateSettingsXml("<profiles>" +
                                             "  <profile>" +
                                             "    <id>one</id>" +
                                             "    <properties>" +
                                             "      <foo>value</foo>" +
                                             "    </properties>" +
                                             "  </profile>" +
                                             "  <profile>" +
                                             "    <id>two</id>" +
                                             "    <properties>" +
                                             "      <foo>value</foo>" +
                                             "    </properties>" +
                                             "  </profile>" +
                                             "</profiles>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

    readWithProfiles("two");

    assertResolved(myProjectPom, findTag(profiles, "settings.profiles[1].properties.foo", MavenDomSettingsModel.class));
  }

  @Test
  public void testResolvingSettingsModelProperties() throws Exception {
    VirtualFile profiles = updateSettingsXml("<localRepository>" + getRepositoryPath() + "</localRepository>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.localRepository}</name>");

    assertResolved(myProjectPom, findTag(profiles, "settings.localRepository", MavenDomSettingsModel.class));
  }

  @Test
  public void testCompletionPropertyInsideSettingsXml() throws Exception {
    VirtualFile profiles = updateSettingsXml("<profiles>" +
                                             "  <profile>" +
                                             "    <id>one</id>" +
                                             "    <properties>" +
                                             "      <foo>value</foo>" +
                                             "      <bar>value</bar>" +
                                             "      <xxx>${<caret>}</xxx>" +
                                             "    </properties>" +
                                             "  </profile>" +
                                             "</profiles>");

    myFixture.configureFromExistingVirtualFile(profiles);
    myFixture.complete(CompletionType.BASIC);
    List<String> strings = myFixture.getLookupElementStrings();

    assert strings != null;
    assert strings.containsAll(Arrays.asList("foo", "bar"));
    assert !strings.contains("xxx");
  }

  @Test
  public void testResolvePropertyInsideSettingsXml() throws Exception {
    VirtualFile profiles = updateSettingsXml("<profiles>" +
                                             "  <profile>" +
                                             "    <id>one</id>" +
                                             "    <properties>" +
                                             "      <foo>value</foo>" +
                                             "      <bar>${<caret>foo}</bar>" +
                                             "    </properties>" +
                                             "  </profile>" +
                                             "</profiles>");

    myFixture.configureFromExistingVirtualFile(profiles);
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assert elementAtCaret instanceof XmlTag;
    assertEquals("foo", ((XmlTag)elementAtCaret).getName());
  }

  @Test
  public void testResolvingAbsentSettingsModelProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.localRepository}</name>");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.name"));
  }

  @Test
  public void testResolvingUnknownSettingsModelProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.foo.bar}</name>");

    assertUnresolved(myProjectPom);
  }

  @Test
  public void testResolvingPropertiesInProfilesXml() throws Exception {
    VirtualFile profiles = createProfilesXml("<profile>" +
                                             "  <id>one</id>" +
                                             "  <properties>" +
                                             "    <foo>value</foo>" +
                                             "  </properties>" +
                                             "</profile>" +
                                             "<profile>" +
                                             "  <id>two</id>" +
                                             "  <properties>" +
                                             "    <foo>value</foo>" +
                                             "  </properties>" +
                                             "</profile>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

    readWithProfiles("two");

    assertResolved(myProjectPom, findTag(profiles, "profilesXml.profiles[1].properties.foo", MavenDomProfilesModel.class));
  }

  @Test
  public void testResolvingPropertiesInOldStyleProfilesXml() throws Exception {
    VirtualFile profiles = createProfilesXmlOldStyle("<profile>" +
                                                     "  <id>one</id>" +
                                                     "  <properties>" +
                                                     "    <foo>value</foo>" +
                                                     "  </properties>" +
                                                     "</profile>" +
                                                     "<profile>" +
                                                     "  <id>two</id>" +
                                                     "  <properties>" +
                                                     "    <foo>value</foo>" +
                                                     "  </properties>" +
                                                     "</profile>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

    readWithProfiles("two");

    assertResolved(myProjectPom, findTag(profiles, "profiles[1].properties.foo", MavenDomProfiles.class));
  }

  @Test
  public void testResolvingInheritedProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>foo}</name>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<properties>" +
                                         "  <foo>value</foo>" +
                                         "</properties>");

    assertResolved(myProjectPom, findTag(parent, "project.properties.foo"));
  }

  @Test
  public void testSystemProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>user.home}</name>");

    assertResolved(myProjectPom, MavenPropertiesVirtualFileSystem.getInstance().findSystemProperty(myProject, "user.home").getPsiElement());
  }

  @Test
  public void testEnvProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>env." + getEnvVar() + "}</name>");

    assertResolved(myProjectPom, MavenPropertiesVirtualFileSystem.getInstance().findEnvProperty(myProject, getEnvVar()).getPsiElement());
  }

  @Test
  public void testUpperCaseEnvPropertiesOnWindows() {
    if (!SystemInfo.isWindows) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>env.PATH}</name>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    PsiElement resolved = ref.resolve();
    assertEquals(System.getenv("Path").replaceAll("[^A-Za-z]", ""), ((IProperty)resolved).getValue().replaceAll("[^A-Za-z]", ""));
  }

  @Test
  public void testCaseInsencitiveOnWindows() throws Exception {
    if (!SystemInfo.isWindows) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>env.PaTH}</name>");

    assertUnresolved(myProjectPom);
  }

  @Test
  public void testNotUpperCaseEnvPropertiesOnWindows() throws Exception {
    if (!SystemInfo.isWindows) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>env.Path}</name>");

    assertUnresolved(myProjectPom);
  }

  @Test
  public void testHighlightUnresolvedProperties() {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>child</artifactId>\n" +
                     "<version>1</version>\n" +
                     "<name>${<error>xxx</error>}</name>\n" +

                     "<properties>\n" +
                     "  <foo>\n" +
                     "${<error>zzz</error>}\n" +
                     "${<error>pom.maven.build.timestamp</error>}\n" +
                     "${<error>project.maven.build.timestamp</error>}\n" +
                     "${<error>parent.maven.build.timestamp</error>}\n" +
                     "${<error>baseUri</error>}\n" +
                     "${<error>unknownProperty</error>}\n" +
                     "${<error>project.version.bar</error>}\n" +

                     "${maven.build.timestamp}\n" +
                     "${project.parentFile.name}\n" +
                     "${<error>project.parentFile.nameXxx</error>}\n" +
                     "${pom.compileArtifacts.empty}\n" +
                     "${modules.empty}\n" +
                     "${projectDirectory}\n" +
                     "</foo>\n" +
                     "</properties>"
    );

    checkHighlighting();
  }

  @Test
  public void testCompletion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<properties>" +
                     "  <pomProp>value</pomProp>" +
                     "</properties>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <pomProfilesProp>value</pomProfilesProp>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <pomProfilesPropInactive>value</pomProfilesPropInactive>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>" +

                     "<name>${<caret>}</name>");

    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <properties>" +
                      "    <profilesXmlProp>value</profilesXmlProp>" +
                      "  </properties>" +
                      "</profile>");

    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<properties>" +
                    "  <parentPomProp>value</parentPomProp>" +
                    "</properties>" +

                    "<profiles>" +
                    "  <profile>" +
                    "    <id>one</id>" +
                    "    <properties>" +
                    "      <parentPomProfilesProp>value</parentPomProfilesProp>" +
                    "    </properties>" +
                    "  </profile>" +
                    "</profiles>");

    createProfilesXml("parent",
                      "<profile>" +
                      "  <id>one</id>" +
                      "  <properties>" +
                      "    <parentProfilesXmlProp>value</parentProfilesXmlProp>" +
                      "  </properties>" +
                      "</profile>");

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <properties>" +
                      "      <settingsXmlProp>value</settingsXmlProp>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    readWithProfiles("one");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertContain(variants, "pomProp", "pomProfilesProp", "profilesXmlProp");
    assertContain(variants,
                  "parentPomProp",
                  "parentPomProfilesProp",
                  "parentProfilesXmlProp");
    assertContain(variants, "artifactId", "project.artifactId", "pom.artifactId");
    assertContain(variants, "basedir", "project.basedir", "pom.basedir", "project.baseUri", "pom.basedir");
    assert !variants.contains("baseUri");
    assertContain(variants, "maven.build.timestamp");
    assert !variants.contains("project.maven.build.timestamp");
    assertContain(variants, "settingsXmlProp");
    assertContain(variants, "settings.localRepository");
    assertContain(variants, "user.home", "env." + getEnvVar());
  }

  @Test
  public void testDoNotIncludeCollectionPropertiesInCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>}</name>");
    assertCompletionVariantsDoNotInclude(myProjectPom, "project.dependencies", "env.\\=C\\:", "idea.config.path");
  }

  @Test
  public void testCompletingAfterOpenBrace() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret></name>");

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId");
  }

  @Test
  public void testCompletingAfterOpenBraceInOpenTag() {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>");

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId");
  }

  @Test
  public void testCompletingAfterOpenBraceAndSomeText() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${pro<caret></name>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertContain(variants, "project.groupId");
    assertDoNotContain(variants, "groupId");
  }

  @Test
  public void testCompletingAfterOpenBraceAndSomeTextWithDot() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${project.g<caret></name>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertContain(variants, "project.groupId");
    assertDoNotContain(variants, "project.name");
  }

  @Test
  public void testDoNotCompleteAfterNonWordCharacter() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<<caret>/name>");

    assertCompletionVariantsDoNotInclude(myProjectPom, "project.groupId");
  }

  private void readWithProfiles(String... profiles) {
    if (isNewImportingProcess) {
      readWithProfilesViaImportFlow(profiles);
    }
    else {
      myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(Arrays.asList(profiles)));
      waitForReadingCompletion();
    }
  }

  @Override
  protected void readProjects() {
    readWithProfiles();
  }

  private void readWithProfilesViaImportFlow(String... profiles) {
    MavenImportFlow flow = new MavenImportFlow();
    MavenInitialImportContext initialImportContext =
      flow.prepareNewImport(myProject, getMavenProgressIndicator(),
                            new FilesList(myAllPoms),
                            getMavenGeneralSettings(),
                            getMavenImporterSettings(),
                            Arrays.asList(profiles),
                            Collections.emptyList());
    myProjectsManager.initForTests();
    myReadContext = flow.readMavenFiles(initialImportContext, null, null);
    myProjectsManager.setProjectsTree(myReadContext.getProjectsTree());
  }
}
