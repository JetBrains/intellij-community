package circlet.app.idea.integration

import circlet.pipelines.config.api.*
import circlet.pipelines.config.dsl.api.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.testFramework.*
import libraries.coroutines.extra.*
import kotlin.test.*

class SmokeTest : HeavyPlatformTestCase() {

    private var testLifetimeImpl: LifetimeSource? = null

    override fun setUp() {
        testLifetimeImpl = Lifetime.Eternal.nested()
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
        testLifetimeImpl!!.terminate()
    }

    // dsl exists on start
    fun testBuildModelWhenDslExistsFromBeginning() {
        addSpaceKts()
        val modelBuilder = project.service<SpaceKtsModelBuilder>()
        val script = modelBuilder.script.value

        assertNotNull(script, "script should be not null at init")

        assertFalse(script.isScriptEmpty(), "script should be empty at init")
        assertFalse(script.isScriptBuilding(), "model build should not be started until view is shown")

        modelBuilder.requestModel()

        assertFalse(script.isScriptBuilding(), "test run in sync mode. so model build should be finished")

        val newScript = modelBuilder.script.value
        assertNotNull(newScript, "script should be not null after build")
        assertFalse(newScript.isScriptEmpty(), "script should not be empty after build")

        assertEquals(createGoldModel(), newScript.config.value)

    }

    // dsl doesnt exist on start and added later
    @Test
    fun testBuildModelWhenDslDoesnotExistFromBeginning() {
        val modelBuilder = project.service<SpaceKtsModelBuilder>()

        assertNull(modelBuilder.script.value, "script should be null without dsl file")
        addSpaceKts()

        val scriptModel = modelBuilder.script.value
        assertNotNull(scriptModel, "script should be not null after added dsl")
        assertFalse(scriptModel.isScriptEmpty(), "script dsl should not be empty")
    }

    private fun addSpaceKts() {
        project.guessProjectDir()!!.writeChild(".space.kts", myTask)
    }

    private fun ScriptModel.isScriptEmpty(): Boolean {
        val config = this.config.value
        return config != null && config.pipelines.isEmpty() && config.targets.isEmpty() && config.jobs.isEmpty()
    }

    private fun ScriptModel.isScriptBuilding(): Boolean {
        return this.state.value == ScriptState.Building
    }

    private fun createGoldModel(): ScriptConfig {
        val projectElementListener = ProjectConfigBuilder()
        val executor = TestProjectExecutor(projectElementListener)

        object : circlet.pipelines.config.dsl.api.Project(executor) {
            init {
                job("myTask") {
                    parallel {
                        container("hello-world1")
                        container("hello-world2")
                    }
                }
            }
        }

        return projectElementListener.build()
    }
}

class TestProjectExecutor(override val listener: ProjectElementListener) : ProjectExecutor

private const val myTask = """job("myTask") {
    this.parallel {
        container("hello-world1")
        container("hello-world2")
    }
}
"""
