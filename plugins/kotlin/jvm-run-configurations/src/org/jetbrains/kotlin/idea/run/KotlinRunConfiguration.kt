// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getEffectiveTargetName
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.java.JavaLanguageRuntimeType
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
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
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.listeners.RefactoringElementAdapter
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.KotlinRunConfigurationsBundle.message
import org.jetbrains.kotlin.idea.core.isInTestSourceContentKotlinAware
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer.Companion.getStartClassFqName
import org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeFqNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.util.jvmName
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

open class KotlinRunConfiguration(name: String?, runConfigurationModule: JavaRunConfigurationModule, factory: ConfigurationFactory?) :
    JavaRunConfigurationBase(name, runConfigurationModule, factory!!),
    CommonJavaRunConfigurationParameters, RefactoringListenerProvider, InputRedirectAware, TargetEnvironmentAwareRunProfile {

    init {
        runConfigurationModule.setModuleToAnyFirstIfNotSpecified()
    }

    override fun getValidModules(): Collection<Module> = ModuleManager.getInstance(project).modules.toList()

    override fun getSearchScope(): GlobalSearchScope? = ExecutionSearchScopes.executionScope(modules.toList())

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
        super<JavaRunConfigurationBase>.readExternal(element)
        JavaRunConfigurationExtensionManager.instance.readExternal(this, element)
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(element: Element) {
        super<JavaRunConfigurationBase>.writeExternal(element)
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

    fun findMainClassFile(): KtFile? {
        val module = configurationModule?.module ?: return null
        val mainClassName = options.mainClassName?.takeIf { !StringUtil.isEmpty(it) } ?: return null
        return findMainClassFile(module, mainClassName)
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        JavaParametersUtil.checkAlternativeJRE(this)
        ProgramParametersUtil.checkWorkingDirectoryExist(this, project, configurationModule!!.module)
        checkConfigurationIsValid(this)
        val module = configurationModule!!.module
            ?: throw RuntimeConfigurationError(message("run.configuration.error.no.module"))
        val mainClassName = options.mainClassName?.takeIf { !StringUtil.isEmpty(it) } ?:
            throw RuntimeConfigurationError(message("run.configuration.error.no.main.class"))
        val mainFile = findMainClassFile(module, mainClassName) ?:
            throw RuntimeConfigurationWarning(
                message("run.configuration.error.class.not.found", mainClassName, configurationModule!!.moduleName)
            )
        if (!mainFile.hasMainFun()) {
            throw RuntimeConfigurationWarning(message("run.configuration.error.class.no.main.method", mainClassName))
        }
    }

    @Throws(ExecutionException::class)
    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment): RunProfileState? {
        return MyJavaCommandLineState(this, executionEnvironment)
    }

    override fun getRefactoringElementListener(element: PsiElement): RefactoringElementListener? {
        val fqNameBeingRenamed: String? = when (element) {
          is KtDeclarationContainer -> getStartClassFqName(element as KtDeclarationContainer)
            is PsiPackage -> element.qualifiedName
            else -> null
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
        val container = EntryPointContainerFinder.find(element) ?: return
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

    override fun canRunOn(target: TargetEnvironmentConfiguration): Boolean {
        return target.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java) != null
    }

    override fun getDefaultLanguageRuntimeType(): LanguageRuntimeType<*>? {
        return LanguageRuntimeType.EXTENSION_NAME.findExtension(JavaLanguageRuntimeType::class.java)
    }

    override fun getDefaultTargetName(): String? {
        return options.remoteTarget
    }

    override fun setDefaultTargetName(targetName: String?) {
        options.remoteTarget = targetName
    }

    override fun needPrepareTarget(): Boolean {
        return getEffectiveTargetName(project) != null || runsUnderWslJdk()
    }

    override fun getShortenCommandLine(): ShortenCommandLine? {
        return options.shortenClasspath
    }

    override fun setShortenCommandLine(mode: ShortenCommandLine?) {
        options.shortenClasspath = mode
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
            params.setShortenCommandLine(myConfiguration.shortenCommandLine, module.project)
            params.mainClass = myConfiguration.runClass
            setupModulePath(params, module)
            return params
        }

        private fun getClasspathType(configurationModule: RunConfigurationModule?): Int {
            val module = configurationModule!!.module ?: throw CantRunException.noModuleConfigured(configurationModule.moduleName)
            val runClass = myConfiguration.runClass
                ?: throw CantRunException(message("run.configuration.error.run.class.should.be.defined", myConfiguration.name))
            val findMainClassFile = findMainClassFile(module, runClass) ?: throw CantRunException.classNotFound(runClass, module)
            val classModule = ModuleUtilCore.findModuleForPsiElement(findMainClassFile) ?: module
            val virtualFileForMainFun = findMainClassFile.virtualFile ?: throw CantRunException(noFunctionFoundMessage(findMainClassFile))
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

        @Nls
        private fun noFunctionFoundMessage(ktFile: KtFile): String {
            val packageFqName = ktFile.packageFqName
            return message("run.configuration.error.main.not.found.top.level", packageFqName.asString())
        }

        companion object {
            private fun setupModulePath(params: JavaParameters, module: JavaRunConfigurationModule?) {
                if (JavaSdkUtil.isJdkAtLeast(params.jdk, JavaSdkVersion.JDK_1_9)) {
                    DumbService.getInstance(module!!.project).computeWithAlternativeResolveEnabled<PsiJavaModule?, Exception> {
                        JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params.mainClass))
                    }?.let { mainModule ->
                        params.moduleName = mainModule.name
                        JavaParametersUtil.putDependenciesOnModulePath(params, mainModule, false)
                    }
                }
            }
        }
    }

    companion object {

        private fun KtNamedFunction.isAMainCandidate(): Boolean {
            if (isLocal) return false

            val jvmName = jvmName
            if (!(name == "main" && jvmName == null || jvmName == "main")) return false

            // method annotated with @JvmName("main") could be a candidate as well
            val valueParameter = valueParameters.singleOrNull()
            val valueParameterType = valueParameter?.typeReference?.typeElement?.safeAs<KtUserType>()
            val argsIsStringArray =
                // `vararg String` has same semantic for main function as Array<String>
                (valueParameter?.isVarArg == true && valueParameterType?.referencedName == "String") ||
                        // Array<String>
                        valueParameter?.typeReference?.typeElement?.run {
                            // to handle `Array` or `Array?`
                            safeAs<KtUserType>() ?: safeAs<KtNullableType>()?.innerType.safeAs<KtUserType>()
                        }?.run {
                            referencedName == "Array" &&
                                    typeArgumentList?.arguments?.singleOrNull()?.run {
                                        typeReference?.typeElement?.safeAs<KtUserType>()?.referencedName == "String" &&
                                                // <String> / <out String>
                                                projectionKind.run { this == KtProjectionKind.NONE || this == KtProjectionKind.OUT }
                                    } ?: false
                        } ?: false

            val topLevel = isTopLevel
            return topLevel && (
                    // top level could be parameterless
                    name == "main" && jvmName == null && (valueParameters.isEmpty() || argsIsStringArray) ||
                            // but if it has @JvmName("main") it has to have Array<String> parameter
                            (jvmName == "main" && argsIsStringArray)
                    ) ||
                    !topLevel &&
                    // nested main method has to have Array<String> parameter
                    argsIsStringArray &&
                    // has to have @JvmStatic annotation
                    annotationEntries.any { it.shortName?.asString() == "JvmStatic" } &&
                    (getParentOfType<KtObjectDeclaration>(false) != null)
        }

        private fun KtDeclarationContainer.getMainFunCandidates(): List<KtNamedFunction> =
            declarations.filterIsInstance<KtNamedFunction>().filter { it.isAMainCandidate() }

        private fun KtElement.findMainFunCandidates(): List<KtNamedFunction> =
            collectDescendantsOfType<KtNamedFunction>().filter { it.isAMainCandidate() }

        fun findMainClassFile(
            module: Module,
            mainClassName: String,
            shouldUseSlowResolve: Boolean = module.project.shouldUseSlowResolve()
        ): KtFile? {
            val project = module.project.takeUnless { it.isDefault } ?: return null
            val scope = module.getModuleRuntimeScope(true)
            val shortName = StringUtil.getShortName(mainClassName)
            val psiFacade = JavaPsiFacade.getInstance(project)

            fun findPackageNameHeuristically(base: String): String {
                val packageName = StringUtil.getPackageName(base)
                if (packageName.isEmpty()) return packageName
                return packageName.takeIf { psiFacade.findPackage(it) != null } ?: findPackageNameHeuristically(packageName)
            }

            val packageName = findPackageNameHeuristically(mainClassName)
            val dotNotationFqName = if (mainClassName.contains('$')) {
                StringUtil.getQualifiedName(StringUtil.getPackageName(mainClassName), shortName.replace('$', '.'))
            } else {
                mainClassName
            }

            // heuristically here means that it follows some common sense:
            // classes of package a.b.c are located in folder `a/b/c`
            fun findMainClassFileHeuristically(): Collection<KtFile> {
                val className = if (packageName.isEmpty()) dotNotationFqName else dotNotationFqName.substring(packageName.length + 1)
                return psiFacade.findPackage(packageName)?.let { pkg ->
                    pkg.getFiles(scope).filterIsInstance<KtFile>().filter { ktFile ->
                        // for top level functions
                        ktFile.javaFileFacadeFqName.shortName().asString() == shortName ||
                                run {
                                    // or within a nested class-or-object
                                    var parent: KtDeclarationContainer? = ktFile

                                    className.split('.').forEach { name ->
                                        parent = parent?.declarations
                                            ?.filterIsInstance<KtClassOrObject>()
                                            ?.firstOrNull { it.name == name } ?: return@run false
                                    }
                                    parent?.getMainFunCandidates()?.isNotEmpty() ?: false
                                }
                    }
                } ?: emptyList()
            }

            fun findFiles(fqName: String) =
                if (shouldUseSlowResolve) {
                    findMainClassFileHeuristically()
                } else {
                    project.runReadActionInSmartMode {
                        KotlinFileFacadeFqNameIndex.INSTANCE.get(fqName, project, scope).takeIf { it.isNotEmpty() }
                            ?: KotlinFullClassNameIndex.getInstance().get(fqName, project, scope)
                                .flatMap { it.findMainFunCandidates() }
                                .map { it.containingKtFile }
                    }
                }

            return findFiles(dotNotationFqName).firstOrNull { it.hasMainFun(shouldUseSlowResolve) }
        }

        private fun Project.shouldUseSlowResolve(): Boolean =
            takeUnless { it.isDefault }?.let { DumbService.getInstance(this).isDumb } ?: false

        private fun KtFile.hasMainFun(
            shouldUseSlowResolve: Boolean = project.shouldUseSlowResolve()
        ): Boolean {
            val mainFunCandidates = findMainFunCandidates()
            if (shouldUseSlowResolve && mainFunCandidates.size == 1) {
                return true
            }

            return KotlinMainFunctionLocatingService.getInstance().hasMain(mainFunCandidates)
        }

    }

}
