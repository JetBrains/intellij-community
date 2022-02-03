// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.ide.actions.runAnything.RunAnythingContext;
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.*;
import static java.util.stream.Collectors.groupingBy;

/**
 * @author ibessonov
 */
public class MavenRunAnythingProviderTest extends MavenMultiVersionImportingTestCase {

  private DataContext myDataContext;
  private MavenRunAnythingProvider myProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myDataContext = SimpleDataContext.getProjectContext(myProject);
    myProvider = new MavenRunAnythingProvider();
  }

  @Test
  public void testRegularProject() {
    withVariantsFor("", it -> {
      assertContain(it, "clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site");
      Collection<MavenCommandLineOptions.Option> options = MavenCommandLineOptions.getAllOptions();
      assertTrue(it.containsAll(ContainerUtil.map(options, option -> option.getName(true))));
      assertTrue(it.containsAll(ContainerUtil.map(options, option -> option.getName(false))));
      assertDoNotContain(it, "clean:clean", "clean:help", "compiler:testCompile", "compiler:compile", "compiler:help");
    });
  }

  @Test
  public void testSingleMavenProject() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    withVariantsFor("", variants -> {
      Function<String, String> classifier = it -> it.contains(":") ? substringBefore(it, ":") : startsWith(it, "-") ? "-" : "";
      Map<String, List<String>> groupedValues = variants.stream().collect(groupingBy(classifier));
      assertSameElements(groupedValues.keySet(), "", "-", "clean", "compiler", "surefire", "resources", "jar", "install", "deploy", "site");
      assertSameElements(groupedValues.get(""), MavenConstants.BASIC_PHASES);
      assertSameElements(groupedValues.get("clean"), "clean:clean", "clean:help");
      assertSameElements(groupedValues.get("compiler"), "compiler:testCompile", "compiler:compile", "compiler:help");
    });
    withVariantsFor("", it -> {
      assertContain(it, "clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site");
      assertContain(it, "clean:clean", "clean:help", "compiler:testCompile", "compiler:compile", "compiler:help");
    });
    withVariantsFor("clean ", it -> {
      assertDoNotContain(it, "clean", "clean clean");
      assertContain(it, "clean validate", "clean compile", "clean test");
      assertContain(it, "clean clean:clean", "clean clean:help");
    });
    withVariantsFor("clean:clean ", it -> {
      assertDoNotContain(it, "clean:clean", "clean:clean clean:clean");
      assertContain(it, "clean:clean clean", "clean:clean validate", "clean:clean compile", "clean:clean test");
      assertContain(it, "clean:clean clean:help");
    });
  }

  @Test
  public void testMavenProjectWithModules() {
    VirtualFile m1 =
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

    VirtualFile m2 =
      createModulePom("m2", "<groupId>test</groupId>" +
                            "<artifactId>m2</artifactId>" +
                            "<version>1</version>");
    importProjects(m1, m2);
    resolvePlugins();

    withVariantsFor("", "m1", it -> {
      assertContain(it, "war:help", "war:inplace", "war:exploded", "war:war");
      assertContain(it, "compiler:compile", "compiler:help");
    });
    withVariantsFor("", "m2", it -> {
      assertDoNotContain(it, "war:help", "war:inplace", "war:exploded", "war:war");
      assertContain(it, "compiler:compile", "compiler:help");
    });
  }

  private void withVariantsFor(@NotNull String command, @NotNull String moduleName, @NotNull Consumer<List<String>> supplier) {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module module = moduleManager.findModuleByName(moduleName);
    withVariantsFor(new RunAnythingContext.ModuleContext(module), command, supplier);
  }

  private void withVariantsFor(@NotNull String command, @NotNull Consumer<List<String>> supplier) {
    withVariantsFor(new RunAnythingContext.ProjectContext(myProject), command, supplier);
  }

  private void withVariantsFor(@NotNull RunAnythingContext context, @NotNull String command, @NotNull Consumer<List<String>> supplier) {
    DataContext dataContext = SimpleDataContext.getSimpleContext(RunAnythingProvider.EXECUTING_CONTEXT, context, myDataContext);
    List<String> variants = myProvider.getValues(dataContext, "mvn " + command);
    supplier.accept(ContainerUtil.map(variants, it -> trimStart(it, "mvn ")));
  }
}
