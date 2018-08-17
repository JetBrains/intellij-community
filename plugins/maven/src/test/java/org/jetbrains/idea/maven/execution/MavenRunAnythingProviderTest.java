// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenConstants;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.substringBefore;
import static com.intellij.openapi.util.text.StringUtil.trimStart;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.Assert.assertNotEquals;

/**
 * @author ibessonov
 */
public class MavenRunAnythingProviderTest extends MavenImportingTestCase {

  private DataContext myDataContext;
  private MavenRunAnythingProvider myProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myDataContext = SimpleDataContext.getProjectContext(myProject);
    myProvider = new MavenRunAnythingProvider();
  }

  public void testRegularProject() {
    assertEmpty(myProvider.getValues(myDataContext, "maven"));
  }

  public void testSingleMavenProject() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Collection<String> values = myProvider.getValues(myDataContext, "maven");
    assertEquals(40, values.size());

    Map<String, List<String>> groupedValues = values.stream().map(value -> trimStart(value, "maven ")).collect(
      groupingBy(value -> value.contains(":") ? substringBefore(value, ":") : "")
    );
    assertSameElements(groupedValues.keySet(), "", "clean", "compiler", "surefire", "resources", "jar", "install", "deploy", "site");

    assertSameElements(groupedValues.get(""), MavenConstants.BASIC_PHASES);
    assertSameElements(groupedValues.get("clean"), "clean:clean", "clean:help");
    assertSameElements(groupedValues.get("compiler"), "compiler:testCompile", "compiler:compile", "compiler:help");
    // and so on
  }

  public void testMavenProjectWithModules() {
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<build>" +
                          "  <plugins>" +
                          "    <plugin>" +
                          "      <groupId>org.apache.maven.plugins</groupId>" +
                          "      <artifactId>maven-war-plugin</artifactId>" +
                          "      <version>3.2.2</version>" +
                          "    </plugin>" +
                          "  </plugins>" +
                          "</build>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<modules>" +
                  "  <module>m1</module>" +
                  "</modules>");
    resolvePlugins();

    Collection<String> values = myProvider.getValues(myDataContext, "maven");
    assertSameElements(values, "maven project", "maven m1");

    values = myProvider.getValues(myDataContext, "maven something");
    assertSameElements(values, "maven project", "maven m1");


    Collection<String> projectValues = myProvider.getValues(myDataContext, "maven project");
    assertTrue(projectValues.stream().allMatch(value -> value.startsWith("maven project") || value.equals("maven m1")));

    projectValues = myProvider.getValues(myDataContext, "maven project ");
    assertTrue(projectValues.stream().allMatch(value -> value.startsWith("maven project")));


    Collection<String> moduleValues = myProvider.getValues(myDataContext, "maven m1");
    assertTrue(moduleValues.stream().allMatch(value -> value.startsWith("maven m1") || value.equals("maven project")));

    moduleValues = myProvider.getValues(myDataContext, "maven m1 ");
    assertTrue(moduleValues.stream().allMatch(value -> value.startsWith("maven m1")));


    assertNotEquals(new HashSet<>(projectValues), new HashSet<>(moduleValues));

    assertContain((List<String>)moduleValues, "maven m1 war:help", "maven m1 war:inplace", "maven m1 war:exploded", "maven m1 war:war");
    assertDoNotContain((List<String>)projectValues, "maven project war:war");
  }
}
