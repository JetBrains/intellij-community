// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.actions;

import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase;
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.junit.Test;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProjectModelModifierTest extends MavenDomWithIndicesTestCase {
  private static final ExternalLibraryDescriptor COMMONS_IO_LIBRARY_DESCRIPTOR_2_4 =
    new ExternalLibraryDescriptor("commons-io", "commons-io", "2.4", "2.4");

  @Override
  protected void tearDown() throws Exception {
    RunAll.runAll(
      () -> stopMavenImportManager(),
      () -> super.tearDown()
    );
  }

  @Test
  public void testAddExternalLibraryDependency() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")),
                                                  new ExternalLibraryDescriptor("junit", "junit"),
                                                  DependencyScope.COMPILE);
    assertImportingIsInProgress(result);

    assertNotNull(result);
    assertHasDependency(myProjectPom, "junit", "junit");
  }

  private void assertImportingIsInProgress(Promise<Void> result) {
    if (isNewImportingProcess) {
      assertTrue(MavenImportingManager.getInstance(myProject).isImportingInProgress());
    }
    else {
      assertSame(Promise.State.PENDING, result.getState());
    }
  }

  @Test
  public void testAddExternalLibraryDependencyWithEqualMinAndMaxVersions() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Promise<Void> result =
      getExtension().addExternalLibraryDependency(Collections.singletonList(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE);
    assertImportingIsInProgress(result);
    assertNotNull(result);
    assertHasDependency(myProjectPom, "commons-io", "commons-io");
  }

  @Test
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
    assertImportingIsInProgress(result);
    assertHasManagedDependency(myProjectPom, "commons-io", "commons-io");
  }

  @Test
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
    assertImportingIsInProgress(result);
  }

  @Test
  public void testAddLibraryDependencyReleaseVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Promise<Void> result = getExtension().addExternalLibraryDependency(
      Collections.singletonList(getModule("project")), new ExternalLibraryDescriptor("commons-io", "commons-io", "999.999", "999.999"),
      DependencyScope.COMPILE);
    assertNotNull(result);
    assertHasDependency(myProjectPom, "commons-io", "commons-io", "RELEASE");
    assertImportingIsInProgress(result);
  }

  @Test
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
    assertImportingIsInProgress(result);
    assertHasDependency(m1, "test", "m2");
  }

  @Test
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
    assertImportingIsInProgress(result);
    assertHasDependency(m1, "junit", "junit");
  }

  @Test
  public void testChangeLanguageLevel() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Module module = getModule("project");
    assertEquals(LanguageLevel.JDK_1_5, LanguageLevelUtil.getEffectiveLanguageLevel(module));
    Promise<Void> result = getExtension().changeLanguageLevel(module, LanguageLevel.JDK_1_8);
    assertNotNull(result);
    assertImportingIsInProgress(result);
    XmlTag tag = findTag("project.build.plugins.plugin");
    assertNotNull(tag);
    assertEquals("maven-compiler-plugin", tag.getSubTagText("artifactId"));
    XmlTag configuration = tag.findFirstSubTag("configuration");
    assertNotNull(configuration);
    assertEquals("8", configuration.getSubTagText("source"));
    assertEquals("8", configuration.getSubTagText("target"));
  }

  @Test
  public void testChangeLanguageLevelPreview() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    Module module = getModule("project");
    assertEquals(LanguageLevel.JDK_1_5, LanguageLevelUtil.getEffectiveLanguageLevel(module));
    Promise<Void> result = getExtension().changeLanguageLevel(module, LanguageLevel.values()[LanguageLevel.HIGHEST.ordinal() + 1]);
    assertImportingIsInProgress(result);
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

  private String assertHasDependency(VirtualFile pom, final String groupId, final String artifactId, final String version) {
    String pomText = PsiManager.getInstance(myProject).findFile(pom).getText();
    Pattern
      pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" +
                                groupId +
                                "</groupId>\\s*<artifactId>" +
                                artifactId +
                                "</artifactId>\\s*<version>" +
                                version +
                                "</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*");
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


  private MavenProjectModelModifier getExtension() {
    return ContainerUtil.findInstance(JavaProjectModelModifier.EP_NAME.getExtensions(myProject), MavenProjectModelModifier.class);
  }
}
