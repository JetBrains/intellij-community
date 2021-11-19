// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run.script.standalone

import com.intellij.execution.*
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementAdapter
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinRunConfigurationsBundle
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.kotlin.idea.run.script.standalone.KotlinStandaloneScriptRunConfigurationProducer.Companion.pathFromPsiElement
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil.isProjectSourceFile
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class KotlinStandaloneScriptRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?
) : KotlinRunConfiguration(name, JavaRunConfigurationModule(project, true), factory), CommonJavaRunConfigurationParameters,
    RefactoringListenerProvider {
    @JvmField
    @NlsSafe
    var filePath: String? = null

    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment): RunProfileState =
        ScriptCommandLineState(executionEnvironment, this)

    override fun suggestedName() = filePath?.substringAfterLast('/')

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        val group = SettingsEditorGroup<KotlinStandaloneScriptRunConfiguration>()
        group.addEditor(
            ExecutionBundle.message("run.configuration.configuration.tab.title"),
            KotlinStandaloneScriptRunConfigurationEditor(project)
        )
        JavaRunConfigurationExtensionManager.instance.appendEditors(this, group)
        return group
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JavaRunConfigurationExtensionManager.instance.writeExternal(this, element)
        DefaultJDOMExternalizer.writeExternal(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        JavaRunConfigurationExtensionManager.instance.readExternal(this, element)
        DefaultJDOMExternalizer.readExternal(this, element)
    }

    override fun getModules(): Array<Module> {
        val scriptVFile = filePath?.let { LocalFileSystem.getInstance().findFileByIoFile(File(it)) }
        return scriptVFile?.module(project)?.let { arrayOf(it) } ?: emptyArray()
    }

    override fun checkConfiguration() {
        JavaParametersUtil.checkAlternativeJRE(this)
        ProgramParametersUtil.checkWorkingDirectoryExist(this, project, null)
        JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this)

        if (filePath.isNullOrEmpty()) {
            runtimeConfigurationWarning(KotlinRunConfigurationsBundle.message("file.was.not.specified"))
        }
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(filePath))
        if (virtualFile == null || virtualFile.isDirectory) {
            runtimeConfigurationWarning(KotlinRunConfigurationsBundle.message("could.not.find.script.file.0", filePath.toString()))
        }
    }

    private fun runtimeConfigurationWarning(@Nls message: String): Nothing {
        throw RuntimeConfigurationWarning(message)
    }

    // NOTE: this is needed for coverage
    override fun getRunClass(): String? = null

    override fun getPackage(): String? = null

    override fun getRefactoringElementListener(element: PsiElement): RefactoringElementListener? {
        val file = element as? KtFile ?: return null
        val pathFromElement = pathFromPsiElement(file) ?: return null

        if (filePath != pathFromElement) {
            return null
        }

        return object : RefactoringElementAdapter() {
            override fun undoElementMovedOrRenamed(newElement: PsiElement, oldQualifiedName: String) {
                setupFilePath(pathFromPsiElement(newElement) ?: return)
            }

            override fun elementRenamedOrMoved(newElement: PsiElement) {
                setupFilePath(pathFromPsiElement(newElement) ?: return)
            }
        }
    }

    override fun defaultWorkingDirectory(): String? {
        return PathUtil.getParentPath(filePath ?: return null)
    }

    fun setupFilePath(filePath: String) {
        val wasDefaultWorkingDirectory = workingDirectory == null || workingDirectory == defaultWorkingDirectory()
        this.filePath = filePath
        if (wasDefaultWorkingDirectory) {
            this.workingDirectory = defaultWorkingDirectory()
        }
    }
}

private class ScriptCommandLineState(
    environment: ExecutionEnvironment,
    configuration: KotlinStandaloneScriptRunConfiguration
) : BaseJavaApplicationCommandLineState<KotlinStandaloneScriptRunConfiguration>(environment, configuration) {

    override fun createJavaParameters(): JavaParameters {
        val params = commonParameters()

        val filePath = configuration.filePath
            ?: throw CantRunException(KotlinRunConfigurationsBundle.message("dialog.message.script.file.was.not.specified"))

        val scriptVFile = LocalFileSystem.getInstance().findFileByIoFile(File(filePath))
                ?: throw CantRunException(KotlinRunConfigurationsBundle.message("dialog.message.script.file.was.not.found.in.project"))

        params.classPath.add(KotlinArtifacts.instance.kotlinCompiler)

        val scriptClasspath = ScriptConfigurationManager.getInstance(environment.project).getScriptClasspath(scriptVFile)
        scriptClasspath.forEach {
            params.classPath.add(it.presentableUrl)
        }

        params.mainClass = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"
        params.programParametersList.prepend(CompositeParameterTargetedValue().addPathPart(filePath))
        params.programParametersList.prepend("-script")
        params.programParametersList.prepend(CompositeParameterTargetedValue().addPathPart(KotlinArtifacts.instance.kotlincDirectory.absolutePath))
        params.programParametersList.prepend("-kotlin-home")

        val module = scriptVFile.module(environment.project)
        if (module != null) {
            val orderEnumerator = OrderEnumerator.orderEntries(module).withoutSdk().recursively().let {
                if (!ProjectRootsUtil.isInTestSource(scriptVFile, environment.project)) it.productionOnly() else it
            }

            val moduleDependencies = orderEnumerator.classes().pathsList
            if (!moduleDependencies.isEmpty) {
                val classpath = CompositeParameterTargetedValue()
                for ((index, path) in moduleDependencies.pathList.withIndex()) {
                    if (index > 0) {
                        classpath.addPathSeparator()
                    }
                    classpath.addPathPart(path)
                }

                params.programParametersList.prepend(classpath)
                params.programParametersList.prepend("-cp")
            }
        }

        return params
    }

    private fun commonParameters(): JavaParameters {
        val params = JavaParameters()
        setupJavaParameters(params)
        val jreHome = if (configuration.isAlternativeJrePathEnabled) myConfiguration.alternativeJrePath else null
        JavaParametersUtil.configureProject(environment.project, params, JavaParameters.JDK_ONLY, jreHome)
        return params
    }
}

private fun VirtualFile.module(project: Project): Module? {
    if (isProjectSourceFile(project, this)) {
        return ModuleUtilCore.findModuleForFile(this, project)
    }
    return null
}
