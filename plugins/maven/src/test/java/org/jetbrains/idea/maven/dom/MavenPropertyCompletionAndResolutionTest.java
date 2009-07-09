package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;
import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;

public class MavenPropertyCompletionAndResolutionTest extends MavenCompletionAndResolutionTestCase {
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

  public void testResolutionToPomProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>pom.version}</name>");

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
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     " <directory>dir</directory>" +
                     "</build>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>" +

                                        "<name>${project.build.directory}</name>");
    importProjects(myProjectPom, child);

    createModulePom("child",
                    "<groupId>test</groupId" +
                    "<artifactId>child</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<name>${<caret>project.build.directory}</name>");

    assertResolved(child, findTag(myProjectPom, "project.build.directory"));
  }

  public void testResolutionToInheritedModelPropertiesForRelativeParent() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>project.build.directory}</name>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<build>" +
                                         "  <directory>dir</directory>" +
                                         "</build>");

    assertResolved(myProjectPom, findTag(parent, "project.build.directory"));
  }

  public void testHandleResolutionRecursion() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>project.build.directory}</name>");

    createModulePom("parent",
                    "<groupId>test</groupId" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "  <relativePath>../pom.xml</version>" +
                    "</parent>");

    assertUnresolved(myProjectPom);
  }

  public void testResolutionFromProperties() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>" +

                     "<name>${<caret>foo}</name>");

    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"));
  }

  public void testResolutionWithProfiles() throws Exception {
    importProjectWithProfiles("two");

    createProjectPom("<groupId>test</groupId" +
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

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"));
  }

  public void testResolvingToProfilesBeforeModelsProperties() throws Exception {
    importProjectWithProfiles("one");

    createProjectPom("<groupId>test</groupId" +
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
    importProjectWithProfiles("two");

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

    assertResolved(myProjectPom, findTag(profiles, "settings.profiles[1].properties.foo", MavenDomSettingsModel.class));
  }

  public void testResolvingModelPropertiesInSettingsXml() throws Exception {
    VirtualFile profiles = updateSettingsXml("<localRepository>" + getRepositoryPath() + "</localRepository>");

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>settings.localRepository}</name>");

    assertResolved(myProjectPom, findTag(profiles, "settings.localRepository", MavenDomSettingsModel.class));
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
    importProjectWithProfiles("two");

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

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
    importProjectWithProfiles("two");

    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>foo}</name>");

    assertResolved(myProjectPom, findTag(profiles, "profiles[1].properties.foo", MavenDomProfiles.class));
  }

  public void testResolvingInheritedProperties() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>./parent/pom.xml</version>" +
                     "</parent>" +

                     "<name>${<caret>foo}</name>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<properties>" +
                                         "  <foo>value</foo>" +
                                         "</properties>");

    assertResolved(myProjectPom, findTag(parent, "project.properties.foo"));
  }

  public void testSystemProperties() throws Exception {
    createProjectPom("<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<name>${user.home}</name>");

    //assertResolved(myProjectPom, findTag(parent, "project.properties.foo"));
  }

  private XmlTag findTag(String path) {
    return findTag(myProjectPom, path);
  }

  private XmlTag findTag(VirtualFile file, String path) {
    return findTag(file, path, MavenDomProjectModel.class);
  }

  private XmlTag findTag(VirtualFile file, String path, Class<? extends MavenDomElement> clazz) {
    return MavenDomUtil.findTag(MavenDomUtil.getMavenDomModel(myProject, file, clazz), path);
  }
}
