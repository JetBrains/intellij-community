// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.project.test.base

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.plugin.checkKotlinPluginKind
import org.jetbrains.kotlin.idea.performance.tests.utils.BuildDataProvider
import org.jetbrains.kotlin.idea.performance.tests.utils.TeamCity
import org.jetbrains.kotlin.idea.performance.tests.utils.closeProject
import org.jetbrains.kotlin.idea.performance.tests.utils.commitAllDocuments
import org.jetbrains.kotlin.idea.performance.tests.utils.project.initApp
import org.jetbrains.kotlin.idea.performance.tests.utils.project.initSdk
import org.jetbrains.kotlin.idea.project.test.base.actions.ActionExecutionResultError
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.actions.executors.ActionExecutorFactory
import org.jetbrains.kotlin.idea.project.test.base.actions.executors.ProjectActionExecutor
import org.jetbrains.kotlin.idea.project.test.base.actions.executors.ProjectActionExecutorData
import org.jetbrains.kotlin.idea.project.test.base.elastic.EsMetricUploader
import org.jetbrains.kotlin.idea.project.test.base.elastic.credentialsByEnvVariables
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsData
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

abstract class AbstractProjectBasedTest : UsefulTestCase() {
    private lateinit var jdk: Sdk
    private lateinit var myApplication: TestApplicationManager
    private var nullableProject: Project? = null

    protected val project: Project
        get() = nullableProject
            ?: error("Project was not initialized")

    override fun setUp() {
        super.setUp()

        myApplication = initApp(testRootDisposable)
        jdk = initSdk(testRootDisposable)
    }

    override fun tearDown() {
        runAll(
            { super.tearDown() },
            { commitAllDocuments() },
            { closeOpenProject() },
        )
    }

    private fun closeOpenProject() {
        nullableProject?.let { myApplication.closeProject(it) }
        nullableProject = null
    }

    protected fun test(
        project: ProjectData,
        actions: List<ProjectAction>,
        profile: ProjectBasedTestPreferences,
    ) {
        checkKotlinPluginKind(expectedPluginKind = profile.frontendConfiguration.pluginKind)

        TeamCity.suite("${project.id}_open") {
            nullableProject = ProjectOpener.openProject(project, jdk)
        }

        globalWarmup(actions, project, profile)
        runActions(actions, project, profile)
    }

    private fun globalWarmup(actions: List<ProjectAction>, project: ProjectData, profile: ProjectBasedTestPreferences) {
        if (profile.warmUpIterations == 0) return
        runActions(
            actions,
            project,
            profile.copy(
                actionOnError = ActionOnError.DO_NOTHING,
                uploadResultsToEs = false,
                warmUpIterations = profile.warmUpIterations + profile.iterations,
                iterations = 0,
            ),
            namePrefix = "globalWarmup"
        )
    }

    private fun runActions(
        actions: List<ProjectAction>,
        project: ProjectData,
        profile: ProjectBasedTestPreferences,
        namePrefix: String? = null,
    ) {
        for (action in actions) {
            val name = listOfNotNull(
                project.id,
                namePrefix,
                action.id,
                action.filePath.substringAfterLast('/')
            ).joinToString(separator = "_")

            TeamCity.suite(name) {
                runAction(action, project, profile)
            }
        }
    }


    private fun runAction(action: ProjectAction, projectData: ProjectData, profile: ProjectBasedTestPreferences) {
        val executor = ActionExecutorFactory.createExecutor(action)

        executor.execute(action, projectData, profile, profile.warmUpIterations).handleErrors(profile)
        val iterations = executor.execute(action, projectData, profile, profile.iterations).handleErrors(profile)

        if (profile.uploadResultsToEs) {
            val buildData = BuildDataProvider.getBuildDataFromTeamCity() ?: BuildDataProvider.getLocalBuildData()
            EsMetricUploader.upload(
                action,
                projectData,
                profile.frontendConfiguration.pluginKind,
                iterations,
                buildData.copy(buildTimestamp = startedBuildTimestamp),
                credentialsByEnvVariables(index = "kotlin_fir_ide_benchmarks"),
            )
        }
    }

    private fun ProjectActionExecutor<*, *>.execute(
        action: ProjectAction,
        projectData: ProjectData,
        profile: ProjectBasedTestPreferences,
        count: Int
    ): List<MetricsData> = buildList {
        repeat(count) { iteration ->
            val data = ProjectActionExecutorData(action, iteration, projectData, project, profile)
            val metricsData = execute(data)
            add(metricsData)
        }
    }


    private fun List<MetricsData>.handleErrors(profile: ProjectBasedTestPreferences): List<MetricsData> {
        when (profile.actionOnError) {
            ActionOnError.DO_NOTHING -> {}
            ActionOnError.THROW -> {
                val errors = flatMap(MetricsData::errors)
                errors.firstIsInstanceOrNull<ActionExecutionResultError.ExceptionDuringActionExecution>()?.let { throw it.exception }
                val invalidExecutionResultErrors = errors.filterIsInstance<ActionExecutionResultError.InvalidActionExecutionResult>()
                if (invalidExecutionResultErrors.isNotEmpty()) {
                    val errorsRendered = invalidExecutionResultErrors.joinToString(separator = "\n\n") { it.error }
                    error("Invalid Action Execution Result:\n\n$errorsRendered")
                }
            }
        }
        return this
    }

    companion object {
        // this has to be static, so it's the same for the all tests run during the build
        val startedBuildTimestamp = BuildDataProvider.getBuildTimestamp()
    }
}


abstract class FrontendConfiguration {
    abstract val pluginKind: KotlinPluginKind
    abstract fun invalidateCaches(project: Project)
}

data class ProjectBasedTestPreferences(
    val warmUpIterations: Int,
    val iterations: Int,
    val checkForValidity: Boolean,
    val frontendConfiguration: FrontendConfiguration,
    val uploadResultsToEs: Boolean,
    val actionOnError: ActionOnError,
)

enum class ActionOnError {
    DO_NOTHING,
    THROW
}