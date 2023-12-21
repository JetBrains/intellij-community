// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.Test
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

class MavenRunAnythingProviderTest : MavenMultiVersionImportingTestCase() {
  private var myDataContext: DataContext? = null
  private var myProvider: MavenRunAnythingProvider? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myDataContext = SimpleDataContext.getProjectContext(project)
    myProvider = MavenRunAnythingProvider()
  }

  @Test
  fun testRegularProject() = runBlocking {
    withVariantsFor("") { it: List<String> ->
      assertContain(it, "clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site")
      val options = MavenCommandLineOptions.getAllOptions()
      assertTrue(it.containsAll(ContainerUtil.map(options) { option: MavenCommandLineOptions.Option -> option.getName(true) }))
      assertTrue(it.containsAll(ContainerUtil.map(options) { option: MavenCommandLineOptions.Option -> option.getName(false) }))
      assertDoNotContain(it, "clean:clean", "clean:help", "compiler:testCompile", "compiler:compile", "compiler:help")
    }
  }

  @Test
  fun testSingleMavenProject() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    withVariantsFor("") { variants: List<String> ->
      val classifier = Function { it: String ->
        if (it.contains(":")) StringUtil.substringBefore(it, ":")
        else if (StringUtil.startsWith(it, "-")) "-" else ""
      }
      val groupedValues = variants.stream().collect(Collectors.groupingBy(classifier))
      val expectedValues = arrayOfNotNull(
        "",
        "-",
        "clean",
        "compiler",
        "surefire",
        "resources",
        "jar",
        "install",
        "deploy",
        "site",
        maven4orNull("wrapper"))
      UsefulTestCase.assertSameElements(groupedValues.keys, *expectedValues)
      UsefulTestCase.assertSameElements(groupedValues[""]!!, MavenConstants.BASIC_PHASES)
      UsefulTestCase.assertSameElements(groupedValues["clean"]!!, "clean:clean", "clean:help")
      UsefulTestCase.assertSameElements(groupedValues["compiler"]!!, "compiler:testCompile", "compiler:compile", "compiler:help")
    }
    withVariantsFor("") { it: List<String>? ->
      assertContain(it, "clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site")
      assertContain(it, "clean:clean", "clean:help", "compiler:testCompile", "compiler:compile", "compiler:help")
    }
    withVariantsFor("clean ") { it: List<String>? ->
      assertDoNotContain(it, "clean", "clean clean")
      assertContain(it, "clean validate", "clean compile", "clean test")
      assertContain(it, "clean clean:clean", "clean clean:help")
    }
    withVariantsFor("clean:clean ") { it: List<String>? ->
      assertDoNotContain(it, "clean:clean", "clean:clean clean:clean")
      assertContain(it, "clean:clean clean", "clean:clean validate", "clean:clean compile", "clean:clean test")
      assertContain(it, "clean:clean clean:help")
    }
  }

  @Test
  fun testMavenProjectWithModules() = runBlocking {
    val m1 =
      createModulePom("m1", """
        <groupId>test</groupId>
        <artifactId>m1</artifactId>
        <version>1</version>
        <build>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-war-plugin</artifactId>
              <version>3.2.2</version>
            </plugin>
          </plugins>
        </build>
        """.trimIndent())

    val m2 =
      createModulePom("m2", """
        <groupId>test</groupId>
        <artifactId>m2</artifactId>
        <version>1</version>
        """.trimIndent())
    importProjects(m1, m2)

    withVariantsFor("", "m1") { it: List<String>? ->
      assertContain(it, "war:help", "war:inplace", "war:exploded", "war:war")
      assertContain(it, "compiler:compile", "compiler:help")
    }
    withVariantsFor("", "m2") { it: List<String>? ->
      assertDoNotContain(it, "war:help", "war:inplace", "war:exploded", "war:war")
      assertContain(it, "compiler:compile", "compiler:help")
    }
  }

  private fun withVariantsFor(command: String, moduleName: String, supplier: Consumer<List<String>>) {
    val moduleManager = getInstance(project)
    val module = moduleManager.findModuleByName(moduleName)
    withVariantsFor(RunAnythingContext.ModuleContext(module!!), command, supplier)
  }

  private fun withVariantsFor(command: String, supplier: Consumer<List<String>>) {
    withVariantsFor(RunAnythingContext.ProjectContext(project), command, supplier)
  }

  private fun withVariantsFor(context: RunAnythingContext, command: String, supplier: Consumer<List<String>>) {
    val dataContext = SimpleDataContext.getSimpleContext(RunAnythingProvider.EXECUTING_CONTEXT, context, myDataContext)
    val variants = myProvider!!.getValues(dataContext, "mvn $command")
    supplier.accept(ContainerUtil.map(variants) { it: String? ->
      StringUtil.trimStart(
        it!!, "mvn ")
    })
  }
}
