/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.run

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.diagnostic.logging.LogConfigurationPanel
import com.intellij.execution.*
import com.intellij.execution.InputRedirectAware.InputRedirectOptions
import com.intellij.execution.JavaRunConfigurationExtensionManager.Companion.checkConfigurationIsValid
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExportableOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.refactoring.listeners.RefactoringElementAdapter
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.KotlinJvmBundle.message
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.isInTestSourceContentKotlinAware
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer.Companion.getEntryPointContainer
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer.Companion.getStartClassFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

open class KotlinRunConfiguration(name: String?, runConfigurationModule: JavaRunConfigurationModule, factory: ConfigurationFactory?) :
    ModuleBasedConfiguration<JavaRunConfigurationModule?, Element?>(name, runConfigurationModule, factory!!),
    CommonJavaRunConfigurationParameters, RefactoringListenerProvider, InputRedirectAware {

    init {
        runConfigurationModule.setModuleToAnyFirstIfNotSpecified()
    }

    override fun getValidModules(): Collection<Module> = ModuleManager.getInstance(project).modules.toList()

    override fun getSearchScope(): GlobalSearchScope? = GlobalSearchScopes.executionScope(modules.toList())

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        val group = SettingsEditorGroup<KotlinRunConfiguration>()
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), KotlinRunConfigurationEditor(project))
        JavaRunConfigurationExtensionManager.instance.appendEditors(this, group)
        group.addEditor(ExecutionBundle.message("logs.tab.title"), LogConfigurationPanel())
        return group
    }

    override fun getOptions(): JvmMainMethodRunConfigurationOptions {
        return super.getOptions() as JvmMainMethodRunConfigurationOptions
    }

    override fun readExternal(element: Element) {
        super<ModuleBasedConfiguration>.readExternal(element)
        JavaRunConfigurationExtensionManager.instance.readExternal(this, element)
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(element: Element) {
        super<ModuleBasedConfiguration>.writeExternal(element)
        JavaRunConfigurationExtensionManager.instance.writeExternal(this, element)
    }

    override fun setWorkingDirectory(value: String?) {
        val normalizedValue = if (StringUtil.isEmptyOrSpaces(value)) null else value!!.trim { it <= ' ' }
        val independentValue = PathUtil.toSystemIndependentName(normalizedValue)
        options.workingDirectory = independentValue?.takeIf { it != defaultWorkingDirectory() }
    }

    override fun getWorkingDirectory(): String? =
        options.workingDirectory?.let {
            FileUtilRt.toSystemDependentName(VirtualFileManager.extractPath(it))
        } ?: PathUtil.toSystemDependentName(defaultWorkingDirectory())

    protected open fun defaultWorkingDirectory() = project.basePath

    override fun setVMParameters(value: String?) {
        options.vmParameters = value
    }

    override fun getVMParameters(): String? {
        return options.vmParameters
    }

    override fun setProgramParameters(value: String?) {
        options.programParameters = value
    }

    override fun getProgramParameters(): String? {
        return options.programParameters
    }

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
        options.isPassParentEnv = passParentEnvs
    }

    override fun isPassParentEnvs(): Boolean {
        return options.isPassParentEnv
    }

    override fun getEnvs(): Map<String, String> {
        return options.env
    }

    override fun setEnvs(envs: Map<String, String>) {
        options.env = envs.toMutableMap()
    }

    override fun getRunClass(): String? {
        return options.mainClassName
    }

    fun setRunClass(value: String?) {
        options.mainClassName = value
    }

    override fun getPackage(): String? {
        return null
    }

    override fun isAlternativeJrePathEnabled(): Boolean {
        return options.isAlternativeJrePathEnabled
    }

    override fun setAlternativeJrePathEnabled(enabled: Boolean) {
        options.isAlternativeJrePathEnabled = enabled
    }

    override fun getAlternativeJrePath(): String? {
        return options.alternativeJrePath
    }

    override fun setAlternativeJrePath(path: String?) {
        options.alternativeJrePath = path
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        JavaParametersUtil.checkAlternativeJRE(this)
        ProgramParametersUtil.checkWorkingDirectoryExist(this, project, configurationModule!!.module)
        checkConfigurationIsValid(this)
        val module = configurationModule!!.module
            ?: throw RuntimeConfigurationError(message("run.configuration.error.no.module"))
        val mainClassName = options.mainClassName
        if (StringUtil.isEmpty(mainClassName)) {
            throw RuntimeConfigurationError(message("run.configuration.error.no.main.class"))
        }
        val psiClass = JavaExecutionUtil.findMainClass(module, mainClassName)
        if (psiClass == null) {
            val moduleName = configurationModule!!.moduleName
            throw RuntimeConfigurationWarning(
                message("run.configuration.error.class.not.found", mainClassName!!, moduleName)
            )
        }
        if (findMainFun(psiClass) == null) {
            throw RuntimeConfigurationWarning(
                message(
                    "run.configuration.error.class.no.main.method",
                    mainClassName!!
                )
            )
        }
    }

    @Throws(ExecutionException::class)
    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment): RunProfileState? {
        return MyJavaCommandLineState(this, executionEnvironment)
    }

    override fun getRefactoringElementListener(element: PsiElement): RefactoringElementListener? {
        val fqNameBeingRenamed: String?
        fqNameBeingRenamed = if (element is KtDeclarationContainer) {
            getStartClassFqName((element as KtDeclarationContainer))
        } else if (element is PsiPackage) {
            element.qualifiedName
        } else {
            null
        }
        val mainClassName = options.mainClassName
        if (mainClassName == null ||
            mainClassName != fqNameBeingRenamed && !mainClassName.startsWith("$fqNameBeingRenamed.")
        ) {
            return null
        }
        if (element is KtDeclarationContainer) {
            return object : RefactoringElementAdapter() {
                override fun undoElementMovedOrRenamed(newElement: PsiElement, oldQualifiedName: String) {
                    updateMainClassName(newElement)
                }

                override fun elementRenamedOrMoved(newElement: PsiElement) {
                    updateMainClassName(newElement)
                }
            }
        }
        val nameSuffix = mainClassName.substring(fqNameBeingRenamed!!.length)
        return object : RefactoringElementAdapter() {
            override fun elementRenamedOrMoved(newElement: PsiElement) {
                updateMainClassNameWithSuffix(newElement, nameSuffix)
            }

            override fun undoElementMovedOrRenamed(newElement: PsiElement, oldQualifiedName: String) {
                updateMainClassNameWithSuffix(newElement, nameSuffix)
            }
        }
    }

    private fun updateMainClassName(element: PsiElement) {
        val container = getEntryPointContainer(element) ?: return
        val name = getStartClassFqName(container)
        if (name != null) {
            runClass = name
        }
    }

    private fun updateMainClassNameWithSuffix(element: PsiElement, suffix: String) {
        if (element is PsiPackage) {
            runClass = element.qualifiedName + suffix
        }
    }

    override fun suggestedName(): String? {
        val runClass = runClass
        if (StringUtil.isEmpty(runClass)) {
            return null
        }
        val parts = StringUtil.split(runClass!!, ".")
        return if (parts.isEmpty()) {
            runClass
        } else parts[parts.size - 1]
    }

    override fun getInputRedirectOptions(): InputRedirectOptions {
        return options.redirectOptions
    }

    private class MyJavaCommandLineState(configuration: KotlinRunConfiguration, environment: ExecutionEnvironment?) :
        BaseJavaApplicationCommandLineState<KotlinRunConfiguration?>(environment, configuration) {
        @Throws(ExecutionException::class)
        override fun createJavaParameters(): JavaParameters {
            val params = JavaParameters()
            val module = myConfiguration.configurationModule
            val classPathType = DumbService.getInstance(module!!.project).computeWithAlternativeResolveEnabled<Int, Exception> {
                getClasspathType(module)
            }
            val jreHome = if (myConfiguration.isAlternativeJrePathEnabled) myConfiguration.alternativeJrePath else null
            JavaParametersUtil.configureModule(module, params, classPathType, jreHome)
            setupJavaParameters(params)
            params.setShortenCommandLine(null, module.project)
            params.mainClass = myConfiguration.runClass
            setupModulePath(params, module)
            return params
        }

        private fun getClasspathType(configurationModule: RunConfigurationModule?): Int {
            val module = configurationModule!!.module ?: throw CantRunException.noModuleConfigured(configurationModule.moduleName)
            val runClass = myConfiguration.runClass
                ?: throw CantRunException(String.format("Run class should be defined for configuration '%s'", myConfiguration.name))
            val psiClass = JavaExecutionUtil.findMainClass(module, runClass) ?: throw CantRunException.classNotFound(runClass, module)
            val mainFun = findMainFun(psiClass)
                ?: throw CantRunException(noFunctionFoundMessage(psiClass))
            var classModule = ModuleUtilCore.findModuleForPsiElement(mainFun)
            if (classModule == null) classModule = module
            val virtualFileForMainFun = mainFun.containingFile.virtualFile ?: throw CantRunException(noFunctionFoundMessage(psiClass))
            val fileIndex = ModuleRootManager.getInstance(classModule).fileIndex
            if (fileIndex.isInSourceContent(virtualFileForMainFun)) {
                return if (fileIndex.isInTestSourceContentKotlinAware(virtualFileForMainFun)) {
                    JavaParameters.JDK_AND_CLASSES_AND_TESTS
                } else {
                    JavaParameters.JDK_AND_CLASSES
                }
            }
            val entriesForFile = fileIndex.getOrderEntriesForFile(virtualFileForMainFun)
            for (entry in entriesForFile) {
                if (entry is ExportableOrderEntry && entry.scope == DependencyScope.TEST) {
                    return JavaParameters.JDK_AND_CLASSES_AND_TESTS
                }
            }
            return JavaParameters.JDK_AND_CLASSES
        }

        private fun noFunctionFoundMessage(psiClass: PsiClass): String {
            val classFqName = FqName(psiClass.qualifiedName!!)
            return if (psiClass is KtLightClassForSourceDeclaration) {
                message("run.configuration.error.main.not.found", classFqName)
            } else message("run.configuration.error.main.not.found.top.level", classFqName.parent())
        }

        companion object {
            private fun setupModulePath(params: JavaParameters, module: JavaRunConfigurationModule?) {
                if (JavaSdkUtil.isJdkAtLeast(params.jdk, JavaSdkVersion.JDK_1_9)) {
                    DumbService.getInstance(module!!.project).computeWithAlternativeResolveEnabled<PsiJavaModule?, Exception> {
                        JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params.mainClass))
                    }?.let { mainModule ->
                        params.moduleName = mainModule.name
                        val classPath = params.classPath
                        val modulePath = params.modulePath
                        modulePath.addAll(classPath.pathList)
                        classPath.clear()
                    }
                }
            }
        }
    }

    companion object {
        private fun getMainFunCandidates(psiClass: PsiClass): Collection<KtNamedFunction> {
            return psiClass.allMethods.map { method: PsiMethod ->
                if (method !is KtLightMethod) return@map null
                if (method.getName() != "main") return@map null
                val declaration =
                    method.kotlinOrigin
                if (declaration is KtNamedFunction) declaration else null
            }.filterNotNull()
        }

        private fun findMainFun(psiClass: PsiClass): KtNamedFunction? {
            for (function in getMainFunCandidates(psiClass)) {
                val bindingContext = function.analyze(BodyResolveMode.FULL)
                val mainFunctionDetector = MainFunctionDetector(bindingContext, function.languageVersionSettings)
                if (mainFunctionDetector.isMain(function)) return function
            }
            return null
        }
    }

}