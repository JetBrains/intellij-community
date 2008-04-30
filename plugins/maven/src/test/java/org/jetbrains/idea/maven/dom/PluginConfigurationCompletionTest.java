package org.jetbrains.idea.maven.dom;

import java.io.IOException;
import java.util.List;

public class PluginConfigurationCompletionTest extends MavenCompletionAndResolutionTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testBasicCompletion() throws Exception {
    putCaretInConfigurationSection();
    assertCompletionVariantsInclude(myProjectPom, "source", "target");
  }

  public void testIncludingPropertiesFromAllTheMojos() throws Exception {
    putCaretInConfigurationSection();
    assertCompletionVariantsInclude(myProjectPom, "excludes", "testExcludes");
  }

  public void testDoesNotIncludeNonEditableProperties() throws Exception {
    putCaretInConfigurationSection();
    assertCompletionVariantsDoNotInclude(myProjectPom, "basedir", "buildDirectory");
  }

  private void putCaretInConfigurationSection() throws IOException {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
  }

  public void testNoPropertiesForUnknownPlugin() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>unknown-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }

  public void testNoPropertiesIfNothingIsSpecified() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <configuration>" +
                     "        <<caret>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariants(myProjectPom);
  }

  public void testPropertiesForSpecificGoal() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal>compile</goal>" +
                     "          </goals>" +
                     "          <configuration>" +
                     "            <<caret>" +
                     "          </configuration>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertTrue(variants.toString(), variants.contains("excludes"));
    assertFalse(variants.toString(), variants.contains("testExcludes"));
  }

  public void testPropertiesForSeveralSpecificGoals() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <executions>" +
                     "        <execution>" +
                     "          <goals>" +
                     "            <goal>compile</goal>" +
                     "            <goal>testCompile</goal>" +
                     "          </goals>" +
                     "          <configuration>" +
                     "            <<caret>" +
                     "          </configuration>" +
                     "        </execution>" +
                     "      </executions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    assertCompletionVariantsInclude(myProjectPom, "excludes", "testExcludes");
  }

  public void testDocumentationForProperty() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <s<caret>ource></source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");


    assertDocumentation("The -source argument for the Java compiler.");
  }
}
