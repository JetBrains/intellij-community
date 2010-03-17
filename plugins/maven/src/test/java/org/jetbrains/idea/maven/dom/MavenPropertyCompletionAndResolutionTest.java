/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;
import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem;

import java.util.Arrays;
import java.util.List;

public class MavenPropertyCompletionAndResolutionTest extends MavenDomTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testBasicResolution() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

    assertUnresolved(myProjectPom);
  }

  public void testResolutionToProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  public void testCorrectlyCalculatingTextRangeWithLeadingWhitespaces() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>     ${<caret>project.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

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

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>baseUri}</name>");

    assertResolved(myProjectPom, baseDir);
  }

  public void testBuiltInTimestampProperty() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>maven.build.timestamp}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

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

  public void testResolvingFromPropertiesSection() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>${<caret>project.version}</foo>" +
                     "</properties>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  public void testResolvingFromPropertiesSectionOfNonManagedProject() throws Exception {
    VirtualFile m = createModulePom("module",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>module</artifactId>" +
                                    "<version>1</version>" +

                                    "<properties>" +
                                    "  <foo>${<caret>project.version}</foo>" +
                                    "</properties>");

    assertResolved(m, findTag("project.version"));
  }

  public void testResolutionToUnknownProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.bar}</name>");

    assertUnresolved(myProjectPom);
  }

  public void testResolutionToUnknownExtraProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.version.bar}</name>");

    assertUnresolved(myProjectPom);
  }

  public void testResolutionToAbsentProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.description}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

  public void testResolutionToAbsentPomProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>pom.description}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

  public void testResolutionToAbsentUnclassifiedProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>description}</name>");

    assertResolved(myProjectPom, findTag("project.name"));
  }

  public void testResolutionToPomProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>pom.version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

  public void testResolutionToUnclassifiedProperty() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>version}</name>");

    assertResolved(myProjectPom, findTag("project.version"));
  }

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

  public void testResolutionToInheritedModelPropertiesForManagedParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

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

  public void testResolutionToInheritedSuperPomProjectProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.build.finalName}</name>");

    assertResolved(myProjectPom, findTag(getMavenGeneralSettings().getEffectiveSuperPom(), "project.build.finalName"));
  }

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

  public void testResolvingSettingsModelProperties() throws Exception {
    VirtualFile profiles = updateSettingsXml("<localRepository>" + getRepositoryPath() + "</localRepository>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.localRepository}</name>");

    assertResolved(myProjectPom, findTag(profiles, "settings.localRepository", MavenDomSettingsModel.class));
  }

  public void testResolvingAbsentSettingsModelProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.localRepository}</name>");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.name"));
  }

  public void testResolvingUnknownSettingsModelProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.foo.bar}</name>");

    assertUnresolved(myProjectPom);
  }

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

  public void testSystemProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>user.home}</name>");

    assertResolved(myProjectPom, MavenDomUtil.findProperty(myProject,
                                                           MavenPropertiesVirtualFileSystem.SYSTEM_PROPERTIES_FILE,
                                                           "user.home"));
  }

  public void testEnvProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>env." + getEnvVar() + "}</name>");

    assertResolved(myProjectPom, MavenDomUtil.findProperty(myProject,
                                                           MavenPropertiesVirtualFileSystem.ENV_PROPERTIES_FILE,
                                                           getEnvVar()));
  }

  public void testHighlightUnresolvedProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<error>xxx</error>}</name>" +

                     "<properties>" +
                     "  <foo>${<error>zzz</error>}</foo>" +
                     "</properties>");

    checkHighlighting();
  }

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
    assertContain(variants, "basedir", "project.basedir", "pom.basedir", "baseUri", "project.baseUri", "pom.basedir");
    assertContain(variants, "maven.build.timestamp");
    assertContain(variants, "settingsXmlProp");
    assertContain(variants, "settings.localRepository");
    assertContain(variants, "user.home", "env." + getEnvVar());
  }

  public void testDoNotIncludeCollectionPropertiesInCompletion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>}</name>");
    assertCompletionVariantsDoNotInclude(myProjectPom, "project.dependencies", "env.\\=C\\:", "idea.config.path");
  }

  public void testCompletingAfterOpenBrace() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret></name>");

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId");
  }

  public void testCompletingAfterOpenBraceInOpenTag() throws Exception {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>");

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId");
  }

  public void testCompletingAfterOpenBraceAndSomeText() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${pro<caret></name>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertContain(variants, "project.groupId");
    assertDoNotContain(variants, "groupId");
  }
  
  public void testCompletingAfterOpenBraceAndSomeTextWithDot() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${project.g<caret></name>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertContain(variants, "project.groupId");
    assertDoNotContain(variants, "groupId", "project.name");
  }

  public void testDoNotCompleteAfterNonWordCharacter() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<<caret>/name>");

    assertCompletionVariantsDoNotInclude(myProjectPom, "project.groupId");
  }

  private void readWithProfiles(String... profiles) {
    myProjectsManager.setExplicitProfiles(Arrays.asList(profiles));
    waitForReadingCompletion();
  }
}
