package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.optionValue
import com.intellij.ide.starter.starterTestContext
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Every test writes with the public API and reads the config back from the file, because the file is what the IDE
 * reads on startup. The names of the component and the option are literals here, so a test states which option the
 * IDE gets.
 */
class JpsBuildToolTest {
  @TempDir
  lateinit var tempDir: Path

  private val projectHome: Path get() = tempDir / "projects" / "my-project"
  private val compilerXml: Path get() = projectHome / ".idea" / "compiler.xml"
  private val workspaceXml: Path get() = projectHome / ".idea" / "workspace.xml"

  @Test
  fun `the heap size goes to a config that holds no compiler configuration`() {
    compilerXml.writeConfig("<project version=\"4\" />")

    jpsBuildTool().setBuildProcessHeapSize(2000)

    optionValue(compilerXml, COMPILER_CONFIGURATION, "BUILD_PROCESS_HEAP_SIZE") shouldBe "2000"
  }

  @Test
  fun `the heap size replaces the value the config holds`() {
    compilerXml.writeConfig("""
      <project version="4">
        <component name="CompilerConfiguration">
          <option name="BUILD_PROCESS_HEAP_SIZE" value="700" />
        </component>
      </project>
    """.trimIndent())

    jpsBuildTool().setBuildProcessHeapSize(2000)

    optionValue(compilerXml, COMPILER_CONFIGURATION, "BUILD_PROCESS_HEAP_SIZE") shouldBe "2000"
  }

  @Test
  fun `the heap size joins a compiler configuration that holds another option`() {
    compilerXml.writeConfig("""
      <project version="4">
        <component name="CompilerConfiguration">
          <option name="AUTO_SHOW_ERRORS_IN_EDITOR" value="false" />
        </component>
      </project>
    """.trimIndent())

    jpsBuildTool().setBuildProcessHeapSize(2000)

    optionValue(compilerXml, COMPILER_CONFIGURATION, "AUTO_SHOW_ERRORS_IN_EDITOR") shouldBe "false"
    optionValue(compilerXml, COMPILER_CONFIGURATION, "BUILD_PROCESS_HEAP_SIZE") shouldBe "2000"
  }

  /** The option is looked up in the named component. An option of the same name elsewhere holds another setting. */
  @Test
  fun `an option of another component keeps its value`() {
    compilerXml.writeConfig("""
      <project version="4">
        <component name="AnotherComponent">
          <option name="BUILD_PROCESS_HEAP_SIZE" value="700" />
        </component>
      </project>
    """.trimIndent())

    jpsBuildTool().setBuildProcessHeapSize(2000)

    optionValue(compilerXml, "AnotherComponent", "BUILD_PROCESS_HEAP_SIZE") shouldBe "700"
    optionValue(compilerXml, COMPILER_CONFIGURATION, "BUILD_PROCESS_HEAP_SIZE") shouldBe "2000"
  }

  @Test
  fun `a config file that does not exist stays absent`() {
    projectHome.createDirectories()

    jpsBuildTool().setBuildProcessHeapSize(2000)

    compilerXml.exists() shouldBe false
  }

  @Test
  fun `parallel compilation goes to the workspace config`() {
    workspaceXml.writeConfig("<project version=\"4\" />")

    jpsBuildTool().enableParallelCompilation()

    optionValue(workspaceXml, "CompilerWorkspaceConfiguration", "PARALLEL_COMPILATION") shouldBe "true"
  }

  @Test
  fun `a build VM option goes to a config that holds none`() {
    compilerXml.writeConfig("<project version=\"4\" />")

    jpsBuildTool().addBuildVmOption("jps.use.dependency.graph", "true")

    buildVmOptions() shouldBe "-Djps.use.dependency.graph=true"
  }

  @Test
  fun `a build VM option keeps the options the config holds`() {
    compilerXml.writeConfig("<project version=\"4\" />")

    jpsBuildTool()
      .addBuildVmOption("jps.use.dependency.graph", "true")
      .addBuildVmOption("kotlin.jps.dumb.mode", "true")

    buildVmOptions() shouldBe "-Djps.use.dependency.graph=true -Dkotlin.jps.dumb.mode=true"
  }

  /** The starter calls the method for every test of a run, so the same option must not pile up. */
  @Test
  fun `the same build VM option is added once`() {
    compilerXml.writeConfig("<project version=\"4\" />")

    jpsBuildTool()
      .addBuildVmOption("profiling.mode", "true")
      .addBuildVmOption("profiling.mode", "true")

    buildVmOptions() shouldBe "-Dprofiling.mode=true"
  }

  /**
   * The writer indents the result itself, so a file it read twice must not carry the indentation of both passes.
   * The deepest element is the `option`, which sits two levels below the root, so 4 spaces is the maximum.
   */
  @Test
  fun `a second write adds no indentation`() {
    compilerXml.writeConfig("<project version=\"4\" />")
    val tool = jpsBuildTool()
    tool.setBuildProcessHeapSize(2000)

    tool.addBuildVmOption("profiling.mode", "true")

    val deepestIndent = compilerXml.readText().lines().maxOf { it.takeWhile(Char::isWhitespace).length }
    deepestIndent shouldBe 4
  }

  private fun Path.writeConfig(content: String) {
    parent.createDirectories()
    writeText(content)
  }

  /** The value of the option that holds the VM options of the build process. */
  private fun buildVmOptions(): String? =
    optionValue(compilerXml, COMPILER_CONFIGURATION, "BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")

  private fun jpsBuildTool(): JpsBuildTool = JpsBuildTool(starterTestContext(
    testHome = tempDir / "test-home",
    testName = "jps-build-tool-test",
    projectPath = projectHome,
  ))

  private companion object {
    const val COMPILER_CONFIGURATION = "CompilerConfiguration"
  }
}
