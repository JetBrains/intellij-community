package com.intellij.ide.starter.ide

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.starter.componentElement
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.optionElement
import com.intellij.ide.starter.starterTestContext
import com.intellij.util.ThreeState
import com.intellij.util.xmlb.XmlSerializer
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Every test writes with the public API, reads the config back with the serializer of the platform, and then asks the
 * platform component itself what state it resolves. So a test proves that the record reaches the platform, and not
 * only that the XML looks right.
 *
 * The names of the file, the component and the option are literals here. The production code derives them from the
 * annotations of the platform, so a literal is the only independent check of that derivation.
 */
class ProjectTrustStateTest {
  @TempDir
  lateinit var tempDir: Path

  private val projectHome: Path get() = tempDir / "projects" / "my-project"
  private val configOptions: Path get() = tempDir / "test-home" / "config" / "options"

  @Test
  fun `TRUSTED makes the platform trust the project`() {
    context().setProjectTrusted()

    resolvedStateOf(projectHome) shouldBe ThreeState.YES
  }

  @Test
  fun `NOT_TRUSTED makes the platform distrust the project`() {
    context().setProjectTrustState(ProjectTrustState.NOT_TRUSTED)

    resolvedStateOf(projectHome) shouldBe ThreeState.NO
  }

  /**
   * The starter default is TRUSTED, so a record for the project always exists and every other state has to override
   * it. An earlier textual idempotency check made the second call a no-op.
   */
  @Test
  fun `a later state replaces the record of an earlier one`() {
    val context = context()

    context.setProjectTrusted()
    context.setProjectTrustState(ProjectTrustState.NOT_TRUSTED)

    resolvedStateOf(projectHome) shouldBe ThreeState.NO
    projectState().trustedPaths shouldBe mapOf("$projectHome" to false)
  }

  /** The platform matches a record by the path components, so a path that holds `..` never matches. */
  @Test
  fun `a path that holds a parent step is normalized`() {
    context().setProjectTrustState(ProjectTrustState.NOT_TRUSTED, projectPath = projectHome / "module" / "..")

    projectState().trustedPaths.keys shouldBe setOf("$projectHome")
    resolvedStateOf(projectHome) shouldBe ThreeState.NO
  }

  @Test
  fun `the record lands in the option and the container the IDE reads`() {
    val context = context()

    context.setProjectTrusted()
    context.addTrustedPath(projectHome.parent)

    val trustedPaths = configOptions / TRUSTED_PATHS_FILE
    val entries = optionElement(trustedPaths, PROJECT_STATE_COMPONENT, "TRUSTED_PROJECT_PATHS")?.getChild("map")
      ?.getChildren("entry")
    entries?.map { it.getAttributeValue("key") to it.getAttributeValue("value") } shouldBe
      listOf("$projectHome" to "true")
    val options = optionElement(trustedPaths, SETTINGS_COMPONENT, "TRUSTED_PATHS")?.getChild("list")
      ?.getChildren("option")
    options?.map { it.getAttributeValue("value") } shouldBe listOf("${projectHome.parent}")
  }

  @Test
  fun `addTrustedPath trusts every project under the directory`() {
    context().addTrustedPath(tempDir / "projects")

    trustedInSettings(tempDir / "projects" / "a-project-the-IDE-creates-later") shouldBe true
  }

  @Test
  fun `ASK drops every record that resolves the state, in both components of the file`() {
    val context = context()
    context.setProjectTrusted()
    context.addTrustedPath(projectHome.parent)

    context.setProjectTrustState(ProjectTrustState.ASK)

    resolvedStateOf(projectHome) shouldBe ThreeState.UNSURE
    trustedInSettings(projectHome) shouldBe false
  }

  @Test
  fun `ASK keeps a record of another project`() {
    val otherProject = tempDir / "projects" / "another-project"
    val context = context()
    context.setProjectTrusted(projectPath = otherProject)

    context.setProjectTrustState(ProjectTrustState.ASK)

    resolvedStateOf(projectHome) shouldBe ThreeState.UNSURE
    resolvedStateOf(otherProject) shouldBe ThreeState.YES
  }

  @Test
  fun `a config that already exists keeps its other components`() {
    writeConfig(TRUSTED_PATHS_FILE, """
      <application>
        <component name="SomeOtherComponent">
          <option name="value" value="keep me" />
        </component>
      </application>
    """)

    context().setProjectTrusted()

    componentElement(configOptions / TRUSTED_PATHS_FILE, "SomeOtherComponent") shouldNotBe null
    resolvedStateOf(projectHome) shouldBe ThreeState.YES
  }

  @Test
  fun `Rider gets the project trust state in the trusted paths setting`() {
    riderContext().setProjectTrusted()

    trustedInSettings(projectHome) shouldBe true
    projectState().trustedPaths shouldBe emptyMap()
  }

  @Test
  fun `NOT_TRUSTED is not supported for Rider, whose containers keep trusted paths only`() {
    val error = assertThrows<IllegalArgumentException> {
      riderContext().setProjectTrustState(ProjectTrustState.NOT_TRUSTED)
    }

    error.message shouldContain SETTINGS_COMPONENT
  }

  @Test
  fun `ASK clears the trusted solutions of Rider and the flag that trusts every solution`() {
    val otherSolution = tempDir / "solutions" / "other"
    writeConfig(TRUSTED_SOLUTIONS_FILE, """
      <application>
        <component name="TrustedSolutionStore">
          <option name="trustEverything" value="true" />
          <option name="trustedLocations">
            <set>
              <option value="$projectHome" />
              <option value="$otherSolution" />
            </set>
          </option>
        </component>
      </application>
    """)

    riderContext().setProjectTrustState(ProjectTrustState.ASK)

    val trustedSolutions = configOptions / TRUSTED_SOLUTIONS_FILE
    optionElement(trustedSolutions, TRUSTED_SOLUTION_STORE, "trustEverything") shouldBe null
    optionElement(trustedSolutions, TRUSTED_SOLUTION_STORE, "trustedLocations")?.getChild("set")?.getChildren("option")
      ?.map { it.getAttributeValue("value") } shouldBe listOf("$otherSolution")
  }

  @Test
  fun `NOT_TRUSTED needs a project`() {
    val error = assertThrows<IllegalArgumentException> {
      context(projectPath = null).setProjectTrustState(ProjectTrustState.NOT_TRUSTED)
    }

    error.message shouldContain "needs a project"
  }

  /** A project that has no record asks about trust already, so there is nothing to write. */
  @Test
  fun `ASK takes a context that has no project`() {
    context(projectPath = null).setProjectTrustState(ProjectTrustState.ASK)

    (configOptions / TRUSTED_PATHS_FILE).exists() shouldBe false
  }

  /** A frontend opens no real project path. It gets the record of the backend, or `disableProjectTrustChecks`. */
  @Test
  fun `a frontend writes no record`() {
    context(isFrontend = true).setProjectTrustState(ProjectTrustState.NOT_TRUSTED)

    (configOptions / TRUSTED_PATHS_FILE).exists() shouldBe false
  }

  private fun riderContext(): IDETestContext = context(productCode = IdeInfoType.RIDER.productCode)

  private fun context(
    productCode: String = IdeInfoType.IDEA_ULTIMATE.productCode,
    projectPath: Path? = projectHome,
    isFrontend: Boolean = false,
  ): IDETestContext = starterTestContext(
    testHome = tempDir / "test-home",
    testName = "project-trust-state-test",
    projectPath = projectPath,
    productCode = productCode,
    isFrontend = isFrontend,
  )

  /** The state the platform resolves for [path], as `TrustedProjects.getProjectTrustedState` reads it. */
  private fun resolvedStateOf(path: Path): ThreeState =
    TrustedPaths().also { it.loadState(projectState()) }.getProjectPathTrustedState(path)

  /** Whether the platform trusts [path] through the setting, as `isPathTrustedInSettings` reads it. */
  private fun trustedInSettings(path: Path): Boolean =
    TrustedPathsSettings().also { it.loadState(settingsState()) }.isProjectPathTrusted(path)

  private fun projectState(): TrustedPaths.State =
    loadState(PROJECT_STATE_COMPONENT, TrustedPaths.State::class.java) ?: TrustedPaths.State()

  private fun settingsState(): TrustedPathsSettings.State =
    loadState(SETTINGS_COMPONENT, TrustedPathsSettings.State::class.java) ?: TrustedPathsSettings.State()

  /** The state of [component], read with the serializer of the platform, or `null` when the component is absent. */
  private fun <S : Any> loadState(component: String, stateClass: Class<S>): S? =
    componentElement(configOptions / TRUSTED_PATHS_FILE, component)?.let { XmlSerializer.deserialize(it, stateClass) }

  /** Puts [content] into the config file [fileName], as an earlier IDE run or `copyExistingConfig` leaves it. */
  private fun writeConfig(fileName: String, content: String) {
    configOptions.createDirectories()
    (configOptions / fileName).writeText(content.trimIndent())
  }

  private companion object {
    const val TRUSTED_PATHS_FILE = "trusted-paths.xml"
    const val TRUSTED_SOLUTIONS_FILE = "trustedSolutions.xml"
    const val PROJECT_STATE_COMPONENT = "Trusted.Paths"
    const val SETTINGS_COMPONENT = "Trusted.Paths.Settings"
    const val TRUSTED_SOLUTION_STORE = "TrustedSolutionStore"
  }
}
