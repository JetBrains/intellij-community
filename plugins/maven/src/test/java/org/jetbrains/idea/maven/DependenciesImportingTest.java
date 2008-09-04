package org.jetbrains.idea.maven;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.indices.MavenCustomRepositoryTestFixture;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DependenciesImportingTest extends MavenImportingTestCase {
  public void testLibraryDependency() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");
  }

  public void testSystemDependency() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");
  }

  public void testSystemDependencyWithoutPath() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <scope>system</scope>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project"); // dependency was not added due to reported pom model problem. 
  }

  public void testPreservingDependenciesOrder() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>B</groupId>" +
                  "    <artifactId>B</artifactId>" +
                  "    <version>2</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>A</groupId>" +
                  "    <artifactId>A</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project", "Maven: B:B:2", "Maven: A:A:1");
  }

  public void testIntermoduleDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }


  public void testInterModuleDependenciesWithoutModuleVersions() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesWithoutModuleGroup() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesIfThereArePropertiesInArtifactHeader() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <module2Name>m2</module2Name>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>${module2Name}</artifactId>" +
                          "<version>${project.parent.version}</version>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testOptionalLibraryDependencyIsNotExportable() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib1</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib2</artifactId>" +
                  "    <version>1</version>" +
                  "    <optional>true</optional>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertExportedModuleDeps("project", "Maven: group:lib1:1");
  }

  public void testOptionalModuleDependencyIsNotExportable() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m3</artifactId>" +
                          "    <version>1</version>" +
                          "    <optional>true</optional>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>");

    importProject();

    assertExportedModuleDeps("m1", "m2");
  }

  public void testOnlyCompileAndRuntimeDependenciesAreExported() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>compile</artifactId>" +
                  "    <scope>compile</scope>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>runtime</artifactId>" +
                  "    <scope>runtime</scope>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>test</artifactId>" +
                  "    <scope>test</scope>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>provided</artifactId>" +
                  "    <scope>provided</scope>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>system</artifactId>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${java.home}/lib/tools.jar</systemPath>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertExportedModuleDeps("project", "Maven: test:compile:1", "Maven: test:runtime:1");
  }

  public void testTransitiveDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>id</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();
    resolveProject();
    assertModules("project", "m1", "m2");

    assertModuleLibDeps("m2", "Maven: group:id:1");
    assertModuleLibDeps("m1", "Maven: group:id:1");
  }

  public void testTransitiveLibraryDependencyVersionResolution() throws Exception {
    // this test hanles the case when the particular dependency list cause embedder set
    // the versionRange for the xml-apis:xml-apis:1.0.b2 artifact to null.
    // see http://jira.codehaus.org/browse/MNG-3386

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>dom4j</groupId>" +
                  "    <artifactId>dom4j</artifactId>" +
                  "    <version>1.6.1</version>" +
                  "    <scope>runtime</scope>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "     <groupId>org.apache.ws.commons.util</groupId>" +
                  "     <artifactId>ws-commons-util</artifactId>" +
                  "     <version>1.0.2</version>" +
                  "  </dependency>" +
                  "</dependencies>");
    resolveProject();

    assertModules("project");
    assertModuleLibDep("project", "Maven: xml-apis:xml-apis:1.0.b2");
  }

  public void testExclusionOfTransitiveDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "      <exclusions>" +
                          "        <exclusion>" +
                          "          <groupId>group</groupId>" +
                          "          <artifactId>id</artifactId>" +
                          "        </exclusion>" +
                          "      </exclusions>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>id</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");
    importProject();

    assertModuleLibDeps("m2", "Maven: group:id:1");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");
  }

  public void testDependencyWithEnvironmentProperty() throws Exception {
    String javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "    <version>1.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${java.home}/lib/tools.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + javaHome + "/lib/tools.jar!/");
  }

  public void testDependencyWithEnvironmentENVProperty() throws Exception {
    String envDir = FileUtil.toSystemIndependentName(System.getenv("TEMP"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "    <version>1.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${env.TEMP}/lib/tools.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + envDir + "/lib/tools.jar!/");
  }

  public void testTestJarDependencies() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>artifact</artifactId>" +
                  "    <type>test-jar</type>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project", "Maven: group:artifact:test-jar:tests:1");
  }

  public void testDependencyWithClassifier() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>artifact</artifactId>" +
                  "    <classifier>bar</classifier>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");
    assertModules("project");
    assertModuleLibDeps("project", "Maven: group:artifact:bar:1");
  }

  public void testDependencyWithVersionRangeOnModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>[1, 3]</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>2</version>");

    importProject();

    assertModules("project", "m1", "m2");

    if (ignore()) return;

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");
  }

  public void testPropertiesInInheritedDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>group</groupId>" +
                     "    <artifactId>lib</artifactId>" +
                     "    <version>${project.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>2</version>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>");

    importProject();

    assertModuleLibDep("project", "Maven: group:lib:1");
    assertModuleLibDep("m", "Maven: group:lib:2");
  }

  public void testPropertyInTheModuleDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dep-version>1.2.3</dep-version>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>"
    );

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>group</groupId>" +
                         "    <artifactId>id</artifactId>" +
                         "    <version>${dep-version}</version>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModules("project", "m");
    assertModuleLibDeps("m", "Maven: group:id:1.2.3");
  }

  public void testManagedModuleDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<dependencyManagement>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>group</groupId>" +
                     "      <artifactId>id</artifactId>" +
                     "      <version>1</version>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</dependencyManagement>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>group</groupId>" +
                         "    <artifactId>id</artifactId>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();
    assertModuleLibDeps("m", "Maven: group:id:1");
  }

  public void testPropertyInTheManagedModuleDependencyVersion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dep-version>1</dep-version>" +
                     "</properties>" +

                     "<dependencyManagement>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>group</groupId>" +
                     "      <artifactId>id</artifactId>" +
                     "      <version>${dep-version}</version>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</dependencyManagement>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>group</groupId>" +
                         "    <artifactId>id</artifactId>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModules("project", "m");
    assertModuleLibDeps("m", "Maven: group:id:1");
  }

  public void testPomTypeDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <type>pom</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject(); // shouldn't throw any exception
  }

  public void testPropertyInTheManagedModuleDependencyVersionOfPomType() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <version>1</version>" +
                     "</properties>" +

                     "<dependencyManagement>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>xxx</groupId>" +
                     "      <artifactId>yyy</artifactId>" +
                     "      <version>${version}</version>" +
                     "      <type>pom</type>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</dependencyManagement>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>xxx</groupId>" +
                         "    <artifactId>yyy</artifactId>" +
                         "    <type>pom</type>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModules("project", "m");
    assertModuleLibDeps("m");

    if (ignore()) return;

    MavenProjectModel root = myMavenTree.getRootProjects().get(0);
    List<MavenProjectModel> modules = myMavenTree.getModules(root);

    assertOrderedElementsAreEqual(root.getProblems());
    assertOrderedElementsAreEqual(modules.get(0).getProblems(),
                                  "Unresolved dependency: xxx:yyy:pom:1:compile");
  }

  public void testResolvingFromRepositoriesIfSeveral() throws Exception {
    MavenCustomRepositoryTestFixture fixture = new MavenCustomRepositoryTestFixture(myDir);
    setRepositoryPath(fixture.getTestDataPath("local1"));
    removeFromLocalRepository("junit");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "  <repositories>" +
                  "    <repository>" +
                  "      <id>foo</id>" +
                  "      <url>http://foo.bar</url>" +
                  "    </repository>" +
                  "  </repositories>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    File file = fixture.getTestData("local1/junit/junit/4.0/junit-4.0.pom");
    assertFalse(file.exists());

    resolveProject();

    assertTrue(file.exists());
  }

  public void testDoNotCreateSameLibraryTwice() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    importProject();

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testCreateSeparateLibraryForDifferentArtifactTypeAndClassifier() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <type>war</type>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <classifier>jdk5</classifier>" +
                  "  </dependency>" +
                  "</dependencies>");

    importProject();

    assertProjectLibraries("Maven: junit:junit:4.0",
                           "Maven: junit:junit:war:4.0",
                           "Maven: junit:junit:jdk5:4.0");
    assertModuleLibDeps("project",
                        "Maven: junit:junit:4.0",
                        "Maven: junit:junit:war:4.0",
                        "Maven: junit:junit:jdk5:4.0");
  }

  public void testDoNotResetUserLibraryDependencies() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    Library lib = ProjectLibraryTable.getInstance(myProject).createLibrary("My Library");
    ModifiableRootModel model = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();
    model.addLibraryEntry(lib);
    model.commit();

    assertProjectLibraries("Maven: junit:junit:4.0", "My Library");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0", "My Library");

    importProject();

    assertProjectLibraries("Maven: junit:junit:4.0", "My Library");
    // todo should keep deps' order
    assertModuleLibDeps("project", "My Library", "Maven: junit:junit:4.0");
  }

  public void testRemoveOldTypeLibraryDependencies() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();
    LibraryTable.ModifiableModel tableModel = rootModel.getModuleLibraryTable().getModifiableModel();
    Library lib = tableModel.createLibrary("junit:junit:4.0");
    tableModel.commit();
    //rootModel.addLibraryEntry(lib);
    rootModel.commit();

    assertModuleLibDeps("project", "junit:junit:4.0");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");
    importProject();

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testDoNotResetUserModuleDependencies() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");
    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importSeveralProjects(m1, m2);
    assertModuleModuleDeps("m1", "m2");

    Module module = createModule("my-module");

    ModifiableRootModel model = ModuleRootManager.getInstance(getModule("m1")).getModifiableModel();
    model.addModuleOrderEntry(module);
    model.commit();

    assertModuleModuleDeps("m1", "m2", "my-module");

    importSeveralProjects(m1, m2);

    assertModuleModuleDeps("m1", "my-module", "m2");
  }

  public void testRemoveUnnecessaryMavenizedModuleDepsOnRepomport() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");
    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importSeveralProjects(m1, m2);
    assertModuleModuleDeps("m1", "m2");

    updateModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    importSeveralProjects(m1, m2);
    assertModuleModuleDeps("m1");
  }

  public void testDoNotResetCustomJavadocsAndSources() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    Library lib = ProjectLibraryTable.getInstance(myProject).getLibraryByName("Maven: junit:junit:4.0");
    Library.ModifiableModel model = lib.getModifiableModel();
    for (String each : model.getUrls(OrderRootType.SOURCES)) {
      model.removeRoot(each, OrderRootType.SOURCES);
    }
    for (String each : model.getUrls(JavadocOrderRootType.getInstance())) {
      model.removeRoot(each, JavadocOrderRootType.getInstance());
    }
    model.addRoot("file://foo.sources", OrderRootType.SOURCES);
    model.addRoot("file://foo.javadoc", JavadocOrderRootType.getInstance());
    model.commit();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "file://foo.sources",
                       "file://foo.javadoc");

    importProject();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "file://foo.sources",
                       "file://foo.javadoc");
  }

  private void assertProjectLibraries(String... expectedNames) {
    List<String> actualNames = new ArrayList<String>();
    for (Library each : ProjectLibraryTable.getInstance(myProject).getLibraries()) {
      actualNames.add(each.getName());
    }
    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }
}