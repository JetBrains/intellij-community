package circlet.app.idea.integration

import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.runtime.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.testFramework.*
import kotlinx.coroutines.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import runtime.reactive.*
import kotlin.test.*

/*
class SmokeTest : HeavyPlatformTestCase() {

    private var testLifetimeImpl: LifetimeSource? = null

    override fun setUp() {
        testLifetimeImpl = Lifetime.Eternal.nested()
        runInEdtAndWait {
            super.setUp()
        }
    }

    override fun tearDown() {
        runInEdtAndWait {
            log.catch {
                super.tearDown()
            }
        }
        testLifetimeImpl!!.terminate()
    }

    // dsl exists on start
*/
/*
    fun testBuildModelWhenDslExistsFromBeginning() {
        runBlocking {
            withTimeout(60_000) {
                using { lifetime ->

                    val modelBuilder = withContext(lifetime, Ui) {
                        addSpaceKts()
                        val modelBuilder = project.service<SpaceKtsModelBuilder>()
                        modelBuilder
                    }

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
            }
        }
    }
*//*


    // dsl doesnt exist on start and added later
    @Test
    fun testBuildModelWhenDslDoesnotExistFromBeginning() {
        runBlocking {
            withTimeout(60_000) {
                using { lifetime ->

                    val modelBuilder = withContext(lifetime, Ui) {
                        val modelBuilder = project.service<SpaceKtsModelBuilder>()
                        assertNull(modelBuilder.script.value, "script should be null without dsl file")
                        addSpaceKts()
                        modelBuilder
                    }

                    delay(2_000)
                    delay(2_000)
                    val scriptModel = modelBuilder.script.awaitNotNull(lifetime)

                    withContext(lifetime, Ui) {
                        assertNotNull(scriptModel, "script should be not null after added dsl")
                        assertFalse(scriptModel.isScriptEmpty(), "script dsl should not be empty")
                    }
                }
            }
        }
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

    override fun runInDispatchThread() = false

    private fun createGoldModel(): ScriptConfig {
        return ScriptConfig(
            jobs = listOf(
                ScriptJob(
                    "myTask",
                    steps = StepSequence(
                        listOf(ScriptStep.CompositeStep.Fork(
                            listOf(
                                ScriptStep.Process.Container(
                                    "hello-world1",
                                    ScriptStep.ProcessData(exec = ScriptStep.ProcessExecutable.ContainerExecutable.DefaultCommand(emptyList()))
                                ),
                                ScriptStep.Process.Container(
                                    "hello-world2",
                                    ScriptStep.ProcessData(exec = ScriptStep.ProcessExecutable.ContainerExecutable.DefaultCommand(emptyList()))
                                )
                            )
                        ))
                    ))
            ),
            targets = emptyList(),
            pipelines = emptyList()
        )
    }
}
*/

private const val myTask = """job("myTask") {
    this.parallel {
        container("hello-world1")
        container("hello-world2")
    }
}
"""
