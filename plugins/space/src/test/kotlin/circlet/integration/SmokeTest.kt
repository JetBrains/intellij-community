package circlet.integration

import circlet.pipelines.config.dsl.api.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.ui.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.openapi.components.*
import com.intellij.testFramework.fixtures.*
import com.intellij.util.*
import runtime.reactive.*
import kotlin.test.*


class TestProjectExecutor(override val listener: ProjectElementListener) : ProjectExecutor {
    override val buildNumber: String = "test.0"
    override val vcsRevision: String = "revision.0"
    override val vcsBranch: String = "test"
}

class SmokeTest : JavaCodeInsightFixtureTestCase() {

    private var testLifetimeImpl : LifetimeSource? = null

    private val testLifetime: Lifetime
        get() = testLifetimeImpl!!

    override fun setUp() {
        testLifetimeImpl = Lifetime.Eternal.nested()
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
        testLifetimeImpl!!.terminate()
    }

    override fun getTestDataPath(): String {
        return "src/test/resources/integrationTestGold"
    }

    // dsl exist on start
    fun testBuildModelWhenDslExistsFromBeginning() {
        val project = myFixture.project
        val scriptFileName = "circlet.kts"
        val projectFileFolderName = PathUtil.getFileName(project.basePath!!)
        myFixture.copyFileToProject(scriptFileName, "../$projectFileFolderName/$scriptFileName")

        val circletModelStore = ServiceManager.getService(project, CircletModelStore::class.java)
        val viewModel = circletModelStore.viewModel

        var script = viewModel.script.value
        assertNotNull(script, "script should be not null at init")
        assertTrue(script.isScriptEmpty(), "script should be empty at init")
        assertFalse(viewModel.modelBuildIsRunning.value, "model build should not be started until view is shown")

        val view = CircletScriptsViewFactory().createView(testLifetime, project, viewModel)

        assertNotNull(view, "view should not be null")
        assertFalse(viewModel.modelBuildIsRunning.value, "test run in sync mode. so model build should be finished")
        val newScript = viewModel.script.value
        assertNotNull(newScript, "script should be not null after build")
        assertNotSame(script, newScript, "new instance of script should be created")
        script = newScript
        assertFalse(script.isScriptEmpty(), "script should not be empty after build")

        val projectElementListener = ProjectConfigBuilder()
        val executor = TestProjectExecutor(projectElementListener)

        object : circlet.pipelines.config.dsl.api.Project(executor) {
            init {
                task("myTask") {
                    fork {
                        run("hello-world1")
                        run("hello-world2")
                    }
                }
            }
        }

        val expectedModelConfig = projectElementListener.build()
        assertEquals(expectedModelConfig, script.config)
    }

    private fun ScriptViewModel.isScriptEmpty(): Boolean {
        val config = this.config
        return config.pipelines.isEmpty() && config.targets.isEmpty() && config.tasks.isEmpty()
    }
}
