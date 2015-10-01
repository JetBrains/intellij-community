/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.actions;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.ProjectModelModifier;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase;
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nik
 */
public class MavenProjectModelModifierTest extends MavenDomWithIndicesTestCase {
  public void testAddExternalLibraryDependency() throws IOException {
    importProject("<groupId>test</groupId>" +
                         "<artifactId>project</artifactId>" +
                         "<version>1</version>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")), new CommonsIoLibraryDescriptor(),
                                                  DependencyScope.COMPILE);
    assertNotNull(result);
    String version = assertHasDependency(myProjectPom, "junit", "junit");
    waitUntilImported(result);
    assertModuleLibDep("project", "Maven: junit:junit:" + version);
  }

  public void testAddModuleDependency() throws IOException {
    createTwoModulesPom("m1", "m2");
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>");
    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");
    importProject();

    Promise<Void> result = getExtension().addModuleDependency(getModule("m1"), getModule("m2"), DependencyScope.COMPILE);
    assertNotNull(result);
    assertHasDependency(m1, "test", "m2");
    waitUntilImported(result);
    assertModuleModuleDeps("m1", "m2");
  }

  public void testAddLibraryDependency() throws IOException {
    createTwoModulesPom("m1", "m2");
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>");
    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +
                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>junit</groupId>" +
                          "    <artifactId>junit</artifactId>" +
                          "    <version>4.0</version>" +
                          "    <scope>test</scope>" +
                          "  </dependency>" +
                          "</dependencies>");
    importProject();

    String libName = "Maven: junit:junit:4.0";
    assertModuleLibDep("m2", libName);
    Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libName);
    assertNotNull(library);
    Promise<Void> result = getExtension().addLibraryDependency(getModule("m1"), library, DependencyScope.COMPILE);
    assertNotNull(result);
    assertHasDependency(m1, "junit", "junit");
    waitUntilImported(result);
    assertModuleLibDep("m1", libName);
  }

  private void createTwoModulesPom(final String m1, final String m2) throws IOException {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>" + m1 + "</module>" +
                     "  <module>" + m2 + "</module>" +
                     "</modules>");
  }

  private String assertHasDependency(VirtualFile pom, final String groupId, final String artifactId) {
    String pomText = PsiManager.getInstance(myProject).findFile(pom).getText();
    Pattern
      pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                artifactId + "</artifactId>\\s*<version>(.*)</version>\\s*</dependency>.*");
    Matcher matcher = pattern.matcher(pomText);
    assertTrue(matcher.matches());
    return matcher.group(1);
  }

  private void waitUntilImported(Promise<Void> result) {
    waitForReadingCompletion();
    myProjectsManager.waitForResolvingCompletion();
    myProjectsManager.waitForArtifactsDownloadingCompletion();
    performPostImportTasks();
    myProjectsManager.performScheduledImportInTests();
    assertSame(Promise.State.FULFILLED, result.getState());
  }

  private MavenProjectModelModifier getExtension() {
    return ContainerUtil.findInstance(ProjectModelModifier.EP_NAME.getExtensions(myProject), MavenProjectModelModifier.class);
  }

  private static class CommonsIoLibraryDescriptor extends ExternalLibraryDescriptor {
    public CommonsIoLibraryDescriptor() {
      super("junit", "junit");
    }

    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      return Collections.emptyList();
    }
  }
}
