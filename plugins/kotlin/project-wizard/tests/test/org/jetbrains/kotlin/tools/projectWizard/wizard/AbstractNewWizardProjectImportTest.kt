// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getSettings
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.enableInspectionTool
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplaceUntilWithRangeUntilInspection
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.getKtFile
import org.jetbrains.kotlin.idea.test.KotlinSdkCreationChecker
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.cli.BuildSystem
import org.jetbrains.kotlin.tools.projectWizard.cli.DefaultTestParameters
import org.jetbrains.kotlin.tools.projectWizard.cli.TestWizardService
import org.jetbrains.kotlin.tools.projectWizard.cli.assertSuccess
import org.jetbrains.kotlin.tools.projectWizard.cli.isGradle
import org.jetbrains.kotlin.tools.projectWizard.core.service.Services
import org.jetbrains.kotlin.tools.projectWizard.core.service.ServicesManager
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaServices
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaWizardService
import org.jetbrains.kotlin.tools.projectWizard.wizard.services.TestWizardServices
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractNewWizardProjectImportTest : HeavyPlatformTestCase() {
    abstract fun createWizard(directory: Path, buildSystem: BuildSystem, projectDirectory: Path): Wizard

    lateinit var sdkCreationChecker: KotlinSdkCreationChecker

    override fun setUp() {
        super.setUp()
        setRegistryPropertyForTest("use.jdk.vendor.in.suggested.jdk.name", "false")
        runWriteAction {
            listOf(SDK_NAME, "1.8", "11").forEach { name ->
                PluginTestCaseBase.addJdk(testRootDisposable) {
                    val jdk = JavaSdk.getInstance().createJdk(name, IdeaTestUtil.requireRealJdkHome(), false)
                    val homePath = jdk.homePath ?: return@addJdk jdk
                    if (homePath.isNotEmpty()) {
                        val sdkModificator = jdk.sdkModificator
                        sdkModificator.versionString = jdk.sdkType.getVersionString(jdk)
                        sdkModificator.commitChanges()
                    }
                    jdk
                }
            }
        }
        sdkCreationChecker = KotlinSdkCreationChecker()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { sdkCreationChecker.removeNewKotlinSdk() },
            ThrowableRunnable { super.tearDown() },
            ThrowableRunnable {
                runWriteAction {
                    ProjectJdkTable.getInstance().findJdk(SDK_NAME)?.let(ProjectJdkTable.getInstance()::removeJdk)
                }
            }
        )
    }

    fun doTestGradleKts(directoryPath: String) {
        if (Versions.GRADLE.text.startsWith("6.") && Runtime.version().feature() >= 17) {
            System.err.println("Test cannot be launched under JVM of ver >=17 due to Gradle 6 usage")
            return
        }

        doTest(directoryPath, BuildSystem.GRADLE_KOTLIN_DSL)

        // we need code inside invokeLater in org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsUpdater.notifyRootsChanged
        // to be executed
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        checkScriptConfigurationsIfAny()
    }

    fun doTestGradleGroovy(directoryPath: String) {
        doTest(directoryPath, BuildSystem.GRADLE_GROOVY_DSL)
    }

    fun doTestMaven(directoryPath: String) {
        doTest(directoryPath, BuildSystem.MAVEN)
    }

    private fun doTest(directoryPath: String, buildSystem: BuildSystem) {
        // Enable inspection to avoid "Can't find tools" exception (only reproducible on TeamCity)
        val wrapper = LocalInspectionToolWrapper(ReplaceUntilWithRangeUntilInspection())
        enableInspectionTool(project, wrapper, testRootDisposable)

        val directory = Paths.get(directoryPath)

        val parameters = DefaultTestParameters.fromTestDataOrDefault(directory)
        if (!parameters.runForMaven && buildSystem == BuildSystem.MAVEN) return
        if (!parameters.runForGradleGroovy && buildSystem == BuildSystem.GRADLE_GROOVY_DSL) return

        val tempDirectory = Files.createTempDirectory(null)
        if (buildSystem.isGradle) {
            prepareGradleBuildSystem(tempDirectory)
        }

        runWizard(directory, buildSystem, tempDirectory)
    }

    protected fun runWizard(
        directory: Path,
        buildSystem: BuildSystem,
        tempDirectory: Path
    ) {
        val wizard = createWizard(directory, buildSystem, tempDirectory)

        val projectDependentServices =
            IdeaServices.createScopeDependent(project) +
                    TestWizardServices.createProjectDependent(project) +
                    TestWizardServices.PROJECT_INDEPENDENT
        wizard.apply(projectDependentServices, GenerationPhase.ALL).assertSuccess()
    }

    protected fun prepareGradleBuildSystem(
        directory: Path,
        distributionTypeSettings: DistributionType = DistributionType.WRAPPED
    ) {
        project.serviceOrNull<GradleSettings>()?.apply {
            isOfflineWork = GradleEnvironment.Headless.GRADLE_OFFLINE?.toBoolean() ?: isOfflineWork
            serviceDirectoryPath = GradleEnvironment.Headless.GRADLE_SERVICE_DIRECTORY ?: serviceDirectoryPath
        }

        val settings = GradleProjectSettings().apply {
            externalProjectPath = directory.toString()
            isUseQualifiedModuleNames = true
            gradleJvm = SDK_NAME
            distributionType = distributionTypeSettings
        }

        getSettings(project, SYSTEM_ID).linkProject(settings)
    }

    @OptIn(KaExperimentalApi::class)
    protected fun checkScriptConfigurationsIfAny() {
        val settings = (getSettings(project, SYSTEM_ID) as GradleSettings).linkedProjectsSettings.firstOrNull()
            ?: error("Cannot find linked gradle project: ${project.basePath}")
        val scripts = File(settings.externalProjectPath).walkTopDown().filter {
            it.name.endsWith("gradle.kts")
        }

        scripts.map { it.canonicalFile }.forEach { file ->
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
            val psiFile = project.getKtFile(virtualFile) ?: error("Cannot find KtFile for $file")
            assertTrue(
                "Configuration for ${file.path} is missing",
                psiFile.isProcessedAsKotlinScript()
            )
            analyze(psiFile) {
                val diagnostics =
                    psiFile.diagnostics(KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS).filter { it.severity == KaSeverity.ERROR }
                assert(diagnostics.isEmpty()) {
                    "Diagnostics list should be empty:\n ${diagnostics.joinToString("\n") { it.defaultMessage }}"
                }
            }
        }
    }

    private fun KtFile.isProcessedAsKotlinScript(): Boolean {
        val workspaceModel = this.project.workspaceModel
        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()
        val scriptEntities = workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
            .findEntitiesByUrl(this.alwaysVirtualFile.toVirtualFileUrl(fileUrlManager))
            .filterIsInstance<KotlinScriptEntity>().toList()
        return scriptEntities.any { it.entitySource is KotlinGradleScriptEntitySource }
    }

    companion object {
        private const val SDK_NAME = "defaultSdk"

        fun createWizardTestServiceManager() = ServicesManager(
            IdeaServices.PROJECT_INDEPENDENT + Services.IDEA_INDEPENDENT_SERVICES
        ) { services ->
            services.firstIsInstanceOrNull<TestWizardService>()
                ?: services.firstIsInstanceOrNull<IdeaWizardService>()
                ?: services.firstOrNull()
        }
    }
}
