// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.arrayOfNotNull
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertDoNotContain
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.function.Consumer

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenRunAnythingProviderTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  private var myDataContext: DataContext? = null
  private var myProvider: MavenRunAnythingProvider? = null

  @BeforeEach
  fun setUp() {
    myDataContext = SimpleDataContext.getProjectContext(maven.project)
    myProvider = MavenRunAnythingProvider()
  }

  @Test
  fun testRegularProject() = runBlocking {
    withVariantsFor("") { it: List<String> ->
      assertContain(it, "clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site")
      val options = MavenCommandLineOptions.getAllOptions()
      assertTrue(it.containsAll(options.map { option: MavenCommandLineOptions.Option -> option.getName(true) }))
      assertTrue(it.containsAll(options.map { option: MavenCommandLineOptions.Option -> option.getName(false) }))
      assertDoNotContain(it, "clean:clean", "clean:help", "compiler:testCompile", "compiler:compile", "compiler:help")
    }
  }

  @Test
  fun testSingleMavenProject() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    withVariantsFor("") { variants: List<String> ->
      val groupedValues = variants.groupBy {
        if (it.contains(":")) StringUtil.substringBefore(it, ":")
        else if (StringUtil.startsWith(it, "-")) "-" else ""
      }
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
        "site")
      assertSameElements(groupedValues.keys, *expectedValues)
      assertSameElements(groupedValues[""]!!, MavenConstants.BASIC_PHASES)
      assertSameElements(groupedValues["clean"]!!, "clean:clean", "clean:help")
      assertSameElements(groupedValues["compiler"]!!, "compiler:testCompile", "compiler:compile", "compiler:help")
    }
    withVariantsFor("") { it: List<String> ->
      assertContain(it, "clean", "validate", "compile", "test", "package", "verify", "install", "deploy", "site")
      assertContain(it, "clean:clean", "clean:help", "compiler:testCompile", "compiler:compile", "compiler:help")
    }
    withVariantsFor("clean ") { it: List<String> ->
      assertDoNotContain(it, "clean", "clean clean")
      assertContain(it, "clean validate", "clean compile", "clean test")
      assertContain(it, "clean clean:clean", "clean clean:help")
    }
    withVariantsFor("clean:clean ") { it: List<String> ->
      assertDoNotContain(it, "clean:clean", "clean:clean clean:clean")
      assertContain(it, "clean:clean clean", "clean:clean validate", "clean:clean compile", "clean:clean test")
      assertContain(it, "clean:clean clean:help")
    }
  }

  @Test
  fun testMavenProjectWithModules() = runBlocking {
    val m1 =
      maven.createModulePom("m1", """
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
      maven.createModulePom("m2", """
        <groupId>test</groupId>
        <artifactId>m2</artifactId>
        <version>1</version>
        """.trimIndent())
    maven.importProjectsAsync(m1, m2)

    withVariantsFor("", "m1") { it: List<String> ->
      assertContain(it, "war:help", "war:inplace", "war:exploded", "war:war")
      assertContain(it, "compiler:compile", "compiler:help")
    }
    withVariantsFor("", "m2") { it: List<String> ->
      assertDoNotContain(it, "war:help", "war:inplace", "war:exploded", "war:war")
      assertContain(it, "compiler:compile", "compiler:help")
    }
  }

  private fun withVariantsFor(command: String, moduleName: String, supplier: Consumer<List<String>>) {
    val moduleManager = getInstance(maven.project)
    val module = moduleManager.findModuleByName(moduleName)
    withVariantsFor(RunAnythingContext.ModuleContext(module!!), command, supplier)
  }

  private fun withVariantsFor(command: String, supplier: Consumer<List<String>>) {
    withVariantsFor(RunAnythingContext.ProjectContext(maven.project), command, supplier)
  }

  private fun withVariantsFor(context: RunAnythingContext, command: String, supplier: Consumer<List<String>>) {
    val dataContext = SimpleDataContext.getSimpleContext(RunAnythingProvider.EXECUTING_CONTEXT, context, myDataContext)
    val variants = myProvider!!.getValues(dataContext, "mvn $command")
    supplier.accept(variants.map { it: String? -> it!!.removePrefix("mvn ") })
  }
}
