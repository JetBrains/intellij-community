// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.actions;

import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase;
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProjectModelModifierTest extends MavenDomWithIndicesTestCase {
  private static final ExternalLibraryDescriptor COMMONS_IO_LIBRARY_DESCRIPTOR_2_4 =
    new ExternalLibraryDescriptor("commons-io", "commons-io", "2.4", "2.4");

  public void testAddExternalLibraryDependency() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")),
                                                  new ExternalLibraryDescriptor("junit", "junit"),
                                                  DependencyScope.COMPILE);
    assertNotNull(result);
    String version = assertHasDependency(myProjectPom, "junit", "junit");
    waitUntilImported(result);
    assertModuleLibDep("project", "Maven: junit:junit:" + version);
  }

  public void testAddExternalLibraryDependencyWithEqualMinAndMaxVersions() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE);
    assertNotNull(result);
    assertHasDependency(myProjectPom, "commons-io", "commons-io");
    waitUntilImported(result);
    assertModuleLibDep("project", "Maven: commons-io:commons-io:2.4");
  }

  public void testAddManagedLibraryDependency() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<dependencyManagement>\n" +
                  "    <dependencies>\n" +
                  "        <dependency>\n" +
                  "            <groupId>commons-io</groupId>\n" +
                  "            <artifactId>commons-io</artifactId>\n" +
                  "            <version>2.4</version>\n" +
                  "        </dependency>\n" +
                  "    </dependencies>\n" +
                  "</dependencyManagement>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE);
    assertNotNull(result);
    assertHasManagedDependency(myProjectPom, "commons-io", "commons-io");
    waitUntilImported(result);
    assertModuleLibDep("project", "Maven: commons-io:commons-io:2.4");
  }

  public void testAddManagedLibraryDependencyWithDifferentScope() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<dependencyManagement>\n" +
                  "    <dependencies>\n" +
                  "        <dependency>\n" +
                  "            <groupId>commons-io</groupId>\n" +
                  "            <artifactId>commons-io</artifactId>\n" +
                  "            <version>2.4</version>\n" +
                  "            <scope>test</scope>\n" +
                  "        </dependency>\n" +
                  "    </dependencies>\n" +
                  "</dependencyManagement>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE);
    assertNotNull(result);
    waitUntilImported(result);
    assertModuleLibDepScope("project", "Maven: commons-io:commons-io:2.4", DependencyScope.COMPILE);
  }

  public void testAddLibraryDependencyReleaseVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Promise<Void> result = getExtension().addExternalLibraryDependency(
      Collections.singletonList(getModule("project")), new ExternalLibraryDescriptor("commons-io", "commons-io", "999.999", "999.999"), DependencyScope.COMPILE);
    assertNotNull(result);
    final String version = assertHasDependency(myProjectPom, "commons-io", "commons-io");
    assertEquals("RELEASE", version);
    waitUntilImported(result);

    LibraryOrderEntry dep = null;
    for (OrderEntry e : getRootManager("project").getOrderEntries()) {
      // can be commons-io:commons-io:2.4 or commons-io:commons-io:RELEASE
      if (e instanceof LibraryOrderEntry && e.getPresentableName().startsWith("Maven: commons-io:commons-io:")) {
        dep = (LibraryOrderEntry)e;
      }
    }
    assertNotNull(dep);
  }

  public void testAddModuleDependency() {
    createTwoModulesPom("m1", "m2");
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>");
    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");
    importProject();

    Promise<Void> result = getExtension().addModuleDependency(getModule("m1"), getModule("m2"), DependencyScope.COMPILE, false);
    assertNotNull(result);
    assertHasDependency(m1, "test", "m2");
    waitUntilImported(result);
    assertModuleModuleDeps("m1", "m2");
  }

  public void testAddLibraryDependency() {
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
    Promise<Void> result = getExtension().addLibraryDependency(getModule("m1"), library, DependencyScope.COMPILE, false);
    assertNotNull(result);
    assertHasDependency(m1, "junit", "junit");
    waitUntilImported(result);
    assertModuleLibDep("m1", libName);
  }

  public void testChangeLanguageLevel() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Module module = getModule("project");
    assertEquals(LanguageLevel.JDK_1_5, EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module));
    Promise<Void> result = getExtension().changeLanguageLevel(module, LanguageLevel.JDK_1_8);
    assertNotNull(result);
    XmlTag tag = findTag("project.build.plugins.plugin");
    assertNotNull(tag);
    assertEquals("maven-compiler-plugin", tag.getSubTagText("artifactId"));
    XmlTag configuration = tag.findFirstSubTag("configuration");
    assertNotNull(configuration);
    assertEquals("8", configuration.getSubTagText("source"));
    assertEquals("8", configuration.getSubTagText("target"));

    waitUntilImported(result);
    assertEquals(LanguageLevel.JDK_1_8, EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module));

    getExtension().changeLanguageLevel(module, LanguageLevel.JDK_13_PREVIEW);
    assertEquals("--enable-preview",
                 findTag("project.build.plugins.plugin")
                   .findFirstSubTag("configuration")
                   .getSubTagText("compilerArgs"));
  }

  private void createTwoModulesPom(final String m1, final String m2) {
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
                                artifactId + "</artifactId>\\s*<version>(.*)</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*");
    Matcher matcher = pattern.matcher(pomText);
    assertTrue(matcher.matches());
    return matcher.group(1);
  }

  private void assertHasManagedDependency(VirtualFile pom, final String groupId, final String artifactId) {
    String pomText = PsiManager.getInstance(myProject).findFile(pom).getText();
    Pattern
      pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                artifactId + "</artifactId>\\s*</dependency>.*");
    Matcher matcher = pattern.matcher(pomText);
    assertTrue(matcher.matches());
  }

  private void waitUntilImported(Promise<Void> result) {
    waitForReadingCompletion();
    myProjectsManager.waitForResolvingCompletion();
    myProjectsManager.waitForArtifactsDownloadingCompletion();
    performPostImportTasks();
    myProjectsManager.performScheduledImportInTests();
    assertSame(Promise.State.SUCCEEDED, result.getState());
  }

  private MavenProjectModelModifier getExtension() {
    return ContainerUtil.findInstance(JavaProjectModelModifier.EP_NAME.getExtensions(myProject), MavenProjectModelModifier.class);
  }
}
