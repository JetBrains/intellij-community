package circlet.app.idea.integration

import circlet.pipelines.*
import circlet.pipelines.config.api.*
import circlet.pipelines.config.dsl.api.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.ui.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.openapi.components.*
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.fixtures.impl.*
import com.intellij.util.*
import libraries.coroutines.extra.*
import kotlin.test.*


class TestProjectExecutor(override val listener: ProjectElementListener) : ProjectExecutor {
    override val vcsBranch: String = "test"
}

class SmokeTest : BasePlatformTestCase() {

    private var testLifetimeImpl : LifetimeSource? = null

    private val testLifetime: Lifetime
        get() = testLifetimeImpl!!

    override fun setUp() {
        testLifetimeImpl = Lifetime.Eternal.nested()
        super.setUp()
        myFixture.tearDown()

        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val fixtureBuilder = factory.createLightFixtureBuilder(projectDescriptor)
        val fixture = fixtureBuilder.fixture

        val tempDirFixture = TempDirTestFixtureImpl()
        myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, tempDirFixture)

        myFixture.testDataPath = testDataPath
        myFixture.setUp()
    }

    override fun tearDown() {
        super.tearDown()
        testLifetimeImpl!!.terminate()
    }

    override fun getTestDataPath(): String {
        return "src/test/resources/integrationTestGold"
    }

    // dsl exists on start
    fun testBuildModelWhenDslExistsFromBeginning() {
        val project = myFixture.project
        val projectFileFolderName = PathUtil.getFileName(project.basePath!!)
        myFixture.copyFileToProject(DefaultDslFileName, "../$projectFileFolderName/$DefaultDslFileName")

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

        assertEquals(createGoldModel(), script.config)
    }

    // dsl doesnt exist on start and added later
    fun testBuildModelWhenDslDoesnotExistFromBeginning() {
        val project = myFixture.project

        val circletModelStore = ServiceManager.getService(project, CircletModelStore::class.java)
        val viewModel = circletModelStore.viewModel

        var script = viewModel.script.value
        assertNull(script, "script should be null without dsl file")
        assertFalse(viewModel.modelBuildIsRunning.value, "model build should not be started until view is shown")


        val newScript = viewModel.script.value
        assertNotSame(script, newScript, "new instance of script should be created")
        script = newScript
        assertNotNull(script, "script should be not null after added dsl")
        assertTrue(script.isScriptEmpty(), "script should be empty after added dsl")
    }

    private fun ScriptViewModel.isScriptEmpty(): Boolean {
        val config = this.config
        return config.pipelines.isEmpty() && config.targets.isEmpty() && config.tasks.isEmpty()
    }

    private fun createGoldModel() : ProjectConfig {
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

        return projectElementListener.build()
    }
}
