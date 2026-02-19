package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.tests.LambdaIdeContext
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
private class ApplicationTestScopeHolder(val cs: CoroutineScope)

@Service(Service.Level.PROJECT)
private class ProjectTestScopeHolder(val cs: CoroutineScope)

/**
 * Returns an application-level coroutine scope
 */
context(lambdaIdeContext: LambdaIdeContext)
val applicationScope: CoroutineScope
  get() = ApplicationManager.getApplication().service<ApplicationTestScopeHolder>().cs

/**
 * Returns a project-level coroutine scope for [project]
 */
context(lambdaIdeContext: LambdaIdeContext)
fun getProjectScope(project: Project): CoroutineScope = project.service<ProjectTestScopeHolder>().cs

/**
 * Returns a project-level coroutine scope for the project obtained via [getProject]
 */
context(lambdaIdeContext: LambdaIdeContext)
val projectScope: CoroutineScope
  get() = getProjectScope(getProject())