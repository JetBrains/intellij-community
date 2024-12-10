// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.doNotClearBindingContext
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_FUNCTION_NAME
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.*
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
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.utils.Printer

class CodeFragmentCompiler(private val executionContext: ExecutionContext) {
    fun compile(
        codeFragment: KtCodeFragment, filesToCompile: List<KtFile>,
        compilingStrategy: CodeFragmentCompilingStrategy, bindingContext: BindingContext, moduleDescriptor: ModuleDescriptor
    ): CompilationResult {
        val result = compilingStrategy.stats.startAndMeasureCompilationUnderReadAction {
            doCompile(codeFragment, filesToCompile, bindingContext, moduleDescriptor)
        }
        return result.getOrThrow()
    }

    private fun doCompile(
        codeFragment: KtCodeFragment, filesToCompile: List<KtFile>, bindingContext: BindingContext, moduleDescriptor: ModuleDescriptor,
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

        val fragmentCompilerBackend = IRFragmentCompilerCodegen()

        val compilerConfiguration = CompilerConfiguration().apply {
            languageVersionSettings = codeFragment.languageVersionSettings
            doNotClearBindingContext = true
        }

        val parameterInfo = fragmentCompilerBackend.computeFragmentParameters(executionContext, codeFragment, bindingContext)

        val (classDescriptor, methodDescriptor) = createDescriptorsForCodeFragment(
            codeFragment, Name.identifier(GENERATED_CLASS_NAME), Name.identifier(GENERATED_FUNCTION_NAME),
            parameterInfo, returnType, moduleDescriptorWrapper.packageFragmentForEvaluator
        )

        val codegenFactory = fragmentCompilerBackend.codegenFactory(
            bindingContext, compilerConfiguration, classDescriptor, methodDescriptor, parameterInfo,
        )
        val generationState = GenerationState(
            project, moduleDescriptorWrapper, compilerConfiguration,
            generateDeclaredClassFilter = GeneratedClassFilterForCodeFragment(codeFragment),
        )

        try {
            KotlinCodegenFacade.compileCorrectFiles(filesToCompile, generationState, bindingContext, codegenFactory)
            return fragmentCompilerBackend.extractResult(parameterInfo, generationState)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            throw CodeFragmentCodegenException(e)
        }
    }

    private class GeneratedClassFilterForCodeFragment(private val codeFragment: KtCodeFragment) : GenerationState.GenerateClassFilter() {
        override fun shouldGeneratePackagePart(ktFile: KtFile): Boolean =
            ktFile == codeFragment

        override fun shouldGenerateClass(processingClassOrObject: KtClassOrObject): Boolean =
            processingClassOrObject.containingFile == codeFragment
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
        parameterInfo: K1CodeFragmentParameterInfo,
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

        val typeParameterUpperBoundEraser = TypeParameterUpperBoundEraser(ErasureProjectionComputer())
        val erasureTypeAttributes = ErasureTypeAttributes(TypeUsage.COMMON)

        fun upperBoundIfTypeParameter(type: KotlinType) =
            TypeUtils.getTypeParameterDescriptorOrNull(type)
                ?.let { typeParameterUpperBoundEraser.getErasedUpperBound(it, erasureTypeAttributes) }

        fun eraseTypeArguments(type: KotlinType): KotlinType {
            val erasedArguments = type.arguments.map {
                val erasedType = upperBoundIfTypeParameter(it.type) ?: eraseTypeArguments(it.type)
                if (it.isStarProjection) it else it.replaceType(erasedType)
            }
            return KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
                    type.attributes,
                    type.constructor,
                    erasedArguments,
                    type.isMarkedNullable,
                    type.memberScope
                )
        }

        fun erase(type: KotlinType): KotlinType = upperBoundIfTypeParameter(type) ?: eraseTypeArguments(type)

        val parameters = parameterInfo.smartParameters.mapIndexed { index, parameter ->
            ValueParameterDescriptorImpl(
                methodDescriptor, null, index, Annotations.EMPTY, Name.identifier("p$index"),
                erase(parameter.targetType),
                declaresDefaultValue = false,
                isCrossinline = false,
                isNoinline = false,
                varargElementType = null,
                source = SourceElement.NO_SOURCE
            )
        }

        methodDescriptor.initialize(
            null, classDescriptor.thisAsReceiverParameter, emptyList(), emptyList(),
            parameters, erase(returnType), Modality.FINAL, DescriptorVisibilities.PUBLIC
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

    // NOTE: Without this override, psi2ir complains when introducing new symbol
    // when creating an IrFileImpl in `createEmptyIrFile`.
    override fun getOriginal(): DeclarationDescriptor {
        return this
    }

    val packageFragmentForEvaluator = LazyPackageDescriptor(this, FqName.ROOT, resolveSession, declarationProvider)
    val rootPackageDescriptorWrapper: PackageViewDescriptor =
        object : DeclarationDescriptorImpl(Annotations.EMPTY, FqName.ROOT.shortNameOrSpecial()), PackageViewDescriptor {
            private val rootPackageDescriptor = moduleDescriptor.safeGetPackage(FqName.ROOT)

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

    override fun getPackage(fqName: FqName): PackageViewDescriptor =
        if (fqName != FqName.ROOT) {
            moduleDescriptor.safeGetPackage(fqName)
        } else {
            rootPackageDescriptorWrapper
        }

    private fun ModuleDescriptor.safeGetPackage(fqName: FqName): PackageViewDescriptor =
        try {
            getPackage(fqName)
        } catch (e: InvalidModuleException) {
            throw ProcessCanceledException(e)
        }
}

internal fun getResolutionFacadeForCodeFragment(codeFragment: KtCodeFragment): ResolutionFacade {
    val filesToAnalyze = listOf(codeFragment)
    val kotlinCacheService = KotlinCacheService.getInstance(codeFragment.project)
    return kotlinCacheService.getResolutionFacadeWithForcedPlatform(filesToAnalyze, JvmPlatforms.unspecifiedJvmPlatform)
}
