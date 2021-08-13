// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.backend.common.phaser.then
import org.jetbrains.kotlin.backend.jvm.*
import org.jetbrains.kotlin.backend.jvm.lower.reflectiveAccessLowering
import org.jetbrains.kotlin.backend.jvm.lower.fragmentSharedVariablesLowering
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.CodeFragmentCodegen.Companion.getSharedTypeIfApplicable
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.debugger.evaluate.DebuggerFieldPropertyDescriptor
import org.jetbrains.kotlin.idea.debugger.evaluate.EvaluationStatus
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_FUNCTION_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CompiledDataDescriptor.MethodSignature
import org.jetbrains.kotlin.idea.debugger.evaluate.getResolutionFacadeForCodeFragment
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmDescriptorMangler
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.util.NameProvider
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi2ir.generators.fragments.EvaluatorFragmentInfo
import org.jetbrains.kotlin.psi2ir.generators.fragments.EvaluatorFragmentParameterInfo
import org.jetbrains.kotlin.psi2ir.generators.fragments.FragmentCompilerSymbolTableDecorator
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.data.KtClassOrObjectInfo
import org.jetbrains.kotlin.resolve.lazy.data.KtScriptInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyPackageDescriptor
import org.jetbrains.kotlin.resolve.scopes.ChainedMemberScope
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.utils.Printer

class CodeFragmentCodegenException(val reason: Exception) : Exception()

class CodeFragmentCompiler(private val executionContext: ExecutionContext, private val status: EvaluationStatus) {

    companion object {
        enum class FragmentCompilerBackend {
            JVM,
            JVM_IR
        }

        val KOTLIN_EVALUATOR_FRAGMENT_COMPILER_BACKEND: Key<FragmentCompilerBackend> =
            Key.create("KOTLIN_EVALUATOR_FRAGMENT_COMPILER_BACKEND")
    }

    data class CompilationResult(
        val classes: List<ClassToLoad>,
        val parameterInfo: CodeFragmentParameterInfo,
        val localFunctionSuffixes: Map<CodeFragmentParameter.Dumb, String>,
        val mainMethodSignature: MethodSignature
    )

    fun compile(
        codeFragment: KtCodeFragment, filesToCompile: List<KtFile>,
        bindingContext: BindingContext, moduleDescriptor: ModuleDescriptor
    ): CompilationResult {
        val result = ReadAction.nonBlocking<Result<CompilationResult>> {
            runCatching {
                doCompile(codeFragment, filesToCompile, bindingContext, moduleDescriptor)
            }
        }.executeSynchronously()
        return result.getOrThrow()
    }

    private fun doCompile(
        codeFragment: KtCodeFragment, filesToCompile: List<KtFile>,
        bindingContext: BindingContext, moduleDescriptor: ModuleDescriptor
    ): CompilationResult {
        require(codeFragment is KtBlockCodeFragment || codeFragment is KtExpressionCodeFragment) {
            "Unsupported code fragment type: $codeFragment"
        }

        val project = codeFragment.project
        val resolutionFacade = getResolutionFacadeForCodeFragment(codeFragment)

        @OptIn(FrontendInternals::class)
        val resolveSession = resolutionFacade.getFrontendService(ResolveSession::class.java)
        val moduleDescriptorWrapper = EvaluatorModuleDescriptor(codeFragment, moduleDescriptor, filesToCompile, resolveSession)

        val defaultReturnType = moduleDescriptor.builtIns.unitType
        val returnType = getReturnType(codeFragment, bindingContext, defaultReturnType)

        val fragmentCompilerBackend = executionContext.debugProcess.getUserData(KOTLIN_EVALUATOR_FRAGMENT_COMPILER_BACKEND)

        val compilerConfiguration = CompilerConfiguration().apply {
            languageVersionSettings = codeFragment.languageVersionSettings

            // TODO: Do not understand the implications of this, but enforced by assertions in JvmIrCodegen
            if (fragmentCompilerBackend == FragmentCompilerBackend.JVM_IR) {
                put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
            }
        }

        val parameterInfo = CodeFragmentParameterAnalyzer(executionContext, codeFragment, bindingContext, status).analyze()
        val (classDescriptor, methodDescriptor) = createDescriptorsForCodeFragment(
            codeFragment, Name.identifier(GENERATED_CLASS_NAME), Name.identifier(GENERATED_FUNCTION_NAME),
            parameterInfo, returnType, moduleDescriptorWrapper.packageFragmentForEvaluator
        )

        val codegenInfo = CodeFragmentCodegenInfo(classDescriptor, methodDescriptor, parameterInfo.parameters)
        CodeFragmentCodegen.setCodeFragmentInfo(codeFragment, codegenInfo)

        val generationState = GenerationState.Builder(
            project, ClassBuilderFactories.BINARIES, moduleDescriptorWrapper,
            bindingContext, filesToCompile, compilerConfiguration
        ).apply {
            if (fragmentCompilerBackend == FragmentCompilerBackend.JVM_IR) {
                val mangler = JvmDescriptorMangler(MainFunctionDetector(bindingContext, compilerConfiguration.languageVersionSettings))
                val evaluatorFragmentInfo = EvaluatorFragmentInfo.createWithFragmentParameterInfo(
                    codegenInfo.classDescriptor,
                    codegenInfo.methodDescriptor,
                    codegenInfo.parameters.map { EvaluatorFragmentParameterInfo(it.targetDescriptor, it.isLValue) }
                )
                codegenFactory(
                    JvmIrCodegenFactory(
                        configuration = compilerConfiguration,
                        phaseConfig = null,
                        externalMangler = mangler,
                        externalSymbolTable = FragmentCompilerSymbolTableDecorator(
                            JvmIdSignatureDescriptor(mangler),
                            IrFactoryImpl,
                            evaluatorFragmentInfo,
                            NameProvider.DEFAULT,
                        ),
                        prefixPhases = fragmentSharedVariablesLowering then reflectiveAccessLowering,
                        jvmGeneratorExtensions = object : JvmGeneratorExtensionsImpl(compilerConfiguration) {
                            // Top-level declarations in the project being debugged is served to the compiler as
                            // PSI, not as class files. PSI2IR generate these as "external declarations" and
                            // here we provide a shim from the PSI structures serving the names of facade classes
                            // for top level declarations (as the facade classes do not exist in the PSI but are
                            // created and _named_ during compilation).
                            override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
                                val psiSourceFile =
                                    descriptor.toSourceElement.containingFile as? PsiSourceFile ?: return super.getContainerSource(
                                        descriptor
                                    )
                                return FacadeClassSourceShimForFragmentCompilation(psiSourceFile)
                            }

                            @OptIn(ObsoleteDescriptorBasedAPI::class)
                            override fun isAccessorWithExplicitImplementation(accessor: IrSimpleFunction): Boolean {
                                return (accessor.descriptor as? PropertyAccessorDescriptor)?.hasBody() == true
                            }

                            override fun remapDebuggerFieldPropertyDescriptor(propertyDescriptor: PropertyDescriptor): PropertyDescriptor {
                                return when (propertyDescriptor) {
                                    is DebuggerFieldPropertyDescriptor -> {
                                        val fieldDescriptor = JavaPropertyDescriptor.create(
                                            propertyDescriptor.containingDeclaration,
                                            propertyDescriptor.annotations,
                                            propertyDescriptor.modality,
                                            propertyDescriptor.visibility,
                                            propertyDescriptor.isVar,
                                            Name.identifier(propertyDescriptor.fieldName.removeSuffix("_field")),
                                            propertyDescriptor.source,
                                            /*isStaticFinal= */ false
                                        )
                                        fieldDescriptor.setType(
                                            propertyDescriptor.type,
                                            propertyDescriptor.typeParameters,
                                            propertyDescriptor.dispatchReceiverParameter,
                                            propertyDescriptor.extensionReceiverParameter,
                                            propertyDescriptor.contextReceiverParameters
                                        )
                                        fieldDescriptor
                                    }
                                    else ->
                                        propertyDescriptor
                                }
                            }
                        },
                        evaluatorFragmentInfoForPsi2Ir = evaluatorFragmentInfo
                    )
                )
            }
            generateDeclaredClassFilter(GeneratedClassFilterForCodeFragment(codeFragment))
        }.build()

        try {
            KotlinCodegenFacade.compileCorrectFiles(generationState)

            val classes = collectGeneratedClasses(generationState)
            val methodSignature = getMethodSignature(methodDescriptor, parameterInfo, generationState)
            val functionSuffixes = getLocalFunctionSuffixes(parameterInfo.parameters, generationState.typeMapper)

            generationState.destroy()

            return CompilationResult(classes, parameterInfo, functionSuffixes, methodSignature)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            throw CodeFragmentCodegenException(e)
        } finally {
            CodeFragmentCodegen.clearCodeFragmentInfo(codeFragment)
        }
    }

    private fun collectGeneratedClasses(generationState: GenerationState): List<ClassToLoad> {
        val project = generationState.project

        val useBytecodePatcher = ReflectionCallClassPatcher.isEnabled
        val scope = when (val module = (generationState.module.moduleInfo as? ModuleSourceInfo)?.module) {
            null -> GlobalSearchScope.allScope(project)
            else -> GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
        }

        return generationState.factory.asList()
            .filterCodeFragmentClassFiles()
            .map {
                val rawBytes = it.asByteArray()
                val bytes = if (useBytecodePatcher) ReflectionCallClassPatcher.patch(rawBytes, project, scope) else rawBytes
                ClassToLoad(it.internalClassName, it.relativePath, bytes)
            }
    }

    private fun List<OutputFile>.filterCodeFragmentClassFiles(): List<OutputFile> {
        return filter { classFile ->
            val path = classFile.relativePath
            path == "$GENERATED_CLASS_NAME.class" || (path.startsWith("$GENERATED_CLASS_NAME\$") && path.endsWith(".class"))
        }
    }

    private class GeneratedClassFilterForCodeFragment(private val codeFragment: KtCodeFragment) : GenerationState.GenerateClassFilter() {
        override fun shouldGeneratePackagePart(@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") file: KtFile) = file == codeFragment
        override fun shouldAnnotateClass(processingClassOrObject: KtClassOrObject) = true
        override fun shouldGenerateClass(processingClassOrObject: KtClassOrObject) = processingClassOrObject.containingFile == codeFragment
        override fun shouldGenerateCodeFragment(script: KtCodeFragment) = script == this.codeFragment
        override fun shouldGenerateScript(script: KtScript) = false
    }

    private fun getLocalFunctionSuffixes(
        parameters: List<CodeFragmentParameter.Smart>,
        typeMapper: KotlinTypeMapper
    ): Map<CodeFragmentParameter.Dumb, String> {
        val result = mutableMapOf<CodeFragmentParameter.Dumb, String>()

        for (parameter in parameters) {
            if (parameter.kind != CodeFragmentParameter.Kind.LOCAL_FUNCTION) {
                continue
            }

            val ownerClassName = typeMapper.mapOwner(parameter.targetDescriptor).internalName
            val lastDollarIndex = ownerClassName.lastIndexOf('$').takeIf { it >= 0 } ?: continue
            result[parameter.dumb] = ownerClassName.drop(lastDollarIndex)
        }

        return result
    }

    private fun getMethodSignature(
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo,
        state: GenerationState
    ): MethodSignature {
        val typeMapper = state.typeMapper
        val asmSignature = typeMapper.mapSignatureSkipGeneric(methodDescriptor)

        val asmParameters = parameterInfo.parameters.zip(asmSignature.valueParameters).map { (param, sigParam) ->
            getSharedTypeIfApplicable(param, typeMapper) ?: sigParam.asmType
        }

        return MethodSignature(asmParameters, asmSignature.returnType)
    }

    private fun getReturnType(
        codeFragment: KtCodeFragment,
        bindingContext: BindingContext,
        defaultReturnType: SimpleType
    ): KotlinType {
        return when (codeFragment) {
            is KtExpressionCodeFragment -> {
                val typeInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, codeFragment.getContentElement()]
                typeInfo?.type ?: defaultReturnType
            }
            is KtBlockCodeFragment -> {
                val blockExpression = codeFragment.getContentElement()
                val lastStatement = blockExpression.statements.lastOrNull() ?: return defaultReturnType
                val typeInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, lastStatement]
                typeInfo?.type ?: defaultReturnType
            }
            else -> defaultReturnType
        }
    }

    private fun createDescriptorsForCodeFragment(
        declaration: KtCodeFragment,
        className: Name,
        methodName: Name,
        parameterInfo: CodeFragmentParameterInfo,
        returnType: KotlinType,
        packageFragmentDescriptor: PackageFragmentDescriptor
    ): Pair<ClassDescriptor, FunctionDescriptor> {
        val classDescriptor = ClassDescriptorImpl(
            packageFragmentDescriptor, className, Modality.FINAL, ClassKind.OBJECT,
            emptyList(),
            KotlinSourceElement(declaration),
            false,
            LockBasedStorageManager.NO_LOCKS
        )

        val methodDescriptor = SimpleFunctionDescriptorImpl.create(
            classDescriptor, Annotations.EMPTY, methodName,
            CallableMemberDescriptor.Kind.SYNTHESIZED, classDescriptor.source
        )

        val parameters = parameterInfo.parameters.mapIndexed { index, parameter ->
            ValueParameterDescriptorImpl(
                methodDescriptor, null, index, Annotations.EMPTY, Name.identifier("p$index"),
                parameter.targetType,
                declaresDefaultValue = false,
                isCrossinline = false,
                isNoinline = false,
                varargElementType = null,
                source = SourceElement.NO_SOURCE
            )
        }

        methodDescriptor.initialize(
            null, classDescriptor.thisAsReceiverParameter, emptyList(), emptyList(),
            parameters, returnType, Modality.FINAL, DescriptorVisibilities.PUBLIC
        )

        val memberScope = EvaluatorMemberScopeForMethod(methodDescriptor)

        val constructor = ClassConstructorDescriptorImpl.create(classDescriptor, Annotations.EMPTY, true, classDescriptor.source)
        classDescriptor.initialize(memberScope, setOf(constructor), constructor)

        return Pair(classDescriptor, methodDescriptor)
    }
}

private class EvaluatorMemberScopeForMethod(private val methodDescriptor: SimpleFunctionDescriptor) : MemberScopeImpl() {
    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
        return if (name == methodDescriptor.name) {
            listOf(methodDescriptor)
        } else {
            emptyList()
        }
    }

    override fun getContributedDescriptors(
        kindFilter: DescriptorKindFilter,
        nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> {
        return if (kindFilter.accepts(methodDescriptor) && nameFilter(methodDescriptor.name)) {
            listOf(methodDescriptor)
        } else {
            emptyList()
        }
    }

    override fun getFunctionNames() = setOf(methodDescriptor.name)

    override fun printScopeStructure(p: Printer) {
        p.println(this::class.java.simpleName)
    }
}

private class EvaluatorModuleDescriptor(
    val codeFragment: KtCodeFragment,
    val moduleDescriptor: ModuleDescriptor,
    filesToCompile: List<KtFile>,
    resolveSession: ResolveSession
) : ModuleDescriptor by moduleDescriptor {
    private val declarationProvider = object : PackageMemberDeclarationProvider {
        private val rootPackageFiles =
            filesToCompile.filter { it.packageFqName == FqName.ROOT } + codeFragment

        override fun getPackageFiles() = rootPackageFiles
        override fun containsFile(file: KtFile) = file in rootPackageFiles

        override fun getDeclarationNames() = emptySet<Name>()
        override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean) = emptyList<KtDeclaration>()
        override fun getClassOrObjectDeclarations(name: Name) = emptyList<KtClassOrObjectInfo<*>>()
        override fun getAllDeclaredSubPackages(nameFilter: (Name) -> Boolean) = emptyList<FqName>()
        override fun getFunctionDeclarations(name: Name) = emptyList<KtNamedFunction>()
        override fun getPropertyDeclarations(name: Name) = emptyList<KtProperty>()
        override fun getTypeAliasDeclarations(name: Name) = emptyList<KtTypeAlias>()
        override fun getDestructuringDeclarationsEntries(name: Name) = emptyList<KtDestructuringDeclarationEntry>()
        override fun getScriptDeclarations(name: Name) = emptyList<KtScriptInfo>()
    }

    // NOTE: Without this override, psi2ir complains when introducing new symbol for
    // when creating an IrFileImpl in `createEmptyIrFile`.
    override fun getOriginal(): DeclarationDescriptor {
        return this
    }

    val packageFragmentForEvaluator = LazyPackageDescriptor(this, FqName.ROOT, resolveSession, declarationProvider)
    val rootPackageDescriptorWrapper: PackageViewDescriptor =
        object : DeclarationDescriptorImpl(Annotations.EMPTY, FqName.ROOT.shortNameOrSpecial()), PackageViewDescriptor {
            private val rootPackageDescriptor = moduleDescriptor.getPackage(FqName.ROOT)

            override fun getContainingDeclaration() = rootPackageDescriptor.containingDeclaration

            override val fqName get() = rootPackageDescriptor.fqName
            override val module get() = this@EvaluatorModuleDescriptor

            override val memberScope by lazy {
                if (fragments.isEmpty()) {
                    MemberScope.Empty
                } else {
                    val scopes = fragments.map { it.getMemberScope() } + SubpackagesScope(module, fqName)
                    ChainedMemberScope.create("package view scope for $fqName in ${module.name}", scopes)
                }
            }

            override val fragments = listOf(packageFragmentForEvaluator)

            override fun <R, D> accept(visitor: DeclarationDescriptorVisitor<R, D>, data: D): R {
                return visitor.visitPackageViewDescriptor(this, data)
            }
        }

    override fun getPackage(fqName: FqName): PackageViewDescriptor {
        if (fqName != FqName.ROOT) {
            return moduleDescriptor.getPackage(fqName)
        }
        return rootPackageDescriptorWrapper
    }
}

private val OutputFile.internalClassName: String
    get() = relativePath.removeSuffix(".class").replace('/', '.')
