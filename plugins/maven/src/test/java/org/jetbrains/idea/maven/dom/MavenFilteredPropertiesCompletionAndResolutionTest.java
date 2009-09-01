package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference;
import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel;

public class MavenFilteredPropertiesCompletionAndResolutionTest extends MavenDomTestCase {
  public void testBasic() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${project<caret>.version}abc");

    assertResolved(f, findTag("project.version"));
  }

  public void testCorrectlyCalculatingBaseDir() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${basedir<caret>}abc");

    PsiDirectory baseDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent());
    assertResolved(f, baseDir);
  }

  public void testResolvingToNonManagedParentProperties() throws Exception {
    createProjectSubDir("res");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>parent/pom.xml</relativePath>" +
                     "</parent>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>res</directory>" +
                     "      <filtering>true</filtering>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +
                                         "<packaging>pom</packaging>" +

                                         "<properties>" +
                                         "  <parentProp>value</parentProp>" +
                                         "</properties>");

    importProject();

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=${parentProp<caret>}");

    assertResolved(f, findTag(parent, "project.properties.parentProp"));
  }

  public void testResolvingToProfilesXmlProperties() throws Exception {
    createProjectSubDir("res");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>res</directory>" +
                     "      <filtering>true</filtering>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");

    VirtualFile profiles = createProfilesXml("<profile>" +
                                             "  <id>one</id>" +
                                             "  <properties>" +
                                             "    <profileProp>value</profileProp>" +
                                             "  </properties>" +
                                             "</profile>");
    importProjectWithProfiles("one");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=${profileProp<caret>}");

    assertResolved(f, findTag(profiles, "profilesXml.profiles[0].properties.profileProp", MavenDomProfilesModel.class));
  }

  public void testDoNotResolveOutsideResources() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("foo.properties",
                                         "foo=abc${project<caret>.version}abc");
    assertNoReferences(f, MavenPropertyPsiReference.class);
  }

  public void testDoNotResolveNonFilteredResources() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${project<caret>.version}abc");
    assertNoReferences(f, MavenPropertyPsiReference.class);
  }

  public void testUsingFilters() throws Exception {
    VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${xx<caret>x}abc");
    assertResolved(f, MavenDomUtil.findProperty(myProject, filter, "xxx"));
  }

  public void testCompletionFromFilters() throws Exception {
    createProjectSubFile("filters/filter1.properties", "xxx=1");
    createProjectSubFile("filters/filter2.properties", "yyy=1");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter1.properties</filter>" +
                  "    <filter>filters/filter2.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties", "foo=abc${<caret>}abc");
    assertCompletionVariantsInclude(f, "xxx", "yyy");
  }

  public void testRenaming() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo>value</foo>" +
                  "</properties>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${f<caret>oo}abc");

    assertResolved(f, findTag("project.properties.foo"));

    doRename(f, "bar");

    assertEquals(createPomXml("<groupId>test</groupId>" +
                              "<artifactId>project</artifactId>" +
                              "<version>1</version>" +

                              "<properties>" +
                              "  <bar>value</bar>" +
                              "</properties>" +

                              "<build>" +
                              "  <resources>" +
                              "    <resource>" +
                              "      <directory>res</directory>" +
                              "      <filtering>true</filtering>" +
                              "    </resource>" +
                              "  </resources>" +
                              "</build>"),
                 findPsiFile(myProjectPom).getText());

    assertEquals("foo=abc${bar}abc", findPsiFile(f).getText());
  }

  public void testRenamingFilteredProperty() throws Exception {
    VirtualFile filter = createProjectSubFile("filters/filter.properties", "xxx=1");
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${x<caret>xx}abc");
    assertResolved(f, MavenDomUtil.findProperty(myProject, filter, "xxx"));

    doRename(f, "bar");

    assertEquals("foo=abc${bar}abc", findPsiFile(f).getText());
    assertEquals("bar=1", findPsiFile(filter).getText());
  }

  public void testFilteredPropertiesUsages() throws Exception {

  }
}
