/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.codegen.CodeFragmentCodegen
import org.jetbrains.kotlin.codegen.CodeFragmentCodegen.Companion.getSharedTypeIfApplicable
import org.jetbrains.kotlin.codegen.CodeFragmentCodegenInfo
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.resolve.BindingContext
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo


class OldFragmentCompilerCodegen(
    private val codeFragment: KtCodeFragment
) : FragmentCompilerCodegen {

    override fun initCodegen(
        classDescriptor: ClassDescriptor,
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo
    ) {
        val codegenInfo = CodeFragmentCodegenInfo(classDescriptor, methodDescriptor, parameterInfo.parameters)
        CodeFragmentCodegen.setCodeFragmentInfo(codeFragment, codegenInfo)
    }

    override fun cleanupCodegen() {
        CodeFragmentCodegen.clearCodeFragmentInfo(codeFragment)
    }

    override fun configureCompiler(compilerConfiguration: CompilerConfiguration) {
        // NO-OP
    }

    override fun configureGenerationState(
        builder: GenerationState.Builder,
        bindingContext: BindingContext,
        compilerConfiguration: CompilerConfiguration,
        classDescriptor: ClassDescriptor,
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo
    ) {
        // NO-OP
    }

    override fun computeFragmentParameters(
        executionContext: ExecutionContext,
        codeFragment: KtCodeFragment,
        bindingContext: BindingContext
    ): CodeFragmentParameterInfo {
        return CodeFragmentParameterAnalyzer(executionContext, codeFragment, bindingContext).analyze()
    }

    override fun extractResult(
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo,
        generationState: GenerationState
    ): CodeFragmentCompiler.CompilationResult {
        val classes = collectGeneratedClasses(generationState)
        val methodSignature = getMethodSignature(methodDescriptor, parameterInfo, generationState)
        val functionSuffixes = getLocalFunctionSuffixes(parameterInfo.parameters, generationState.typeMapper)
        return CodeFragmentCompiler.CompilationResult(classes, parameterInfo, functionSuffixes, methodSignature)
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

    private fun getMethodSignature(
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo,
        state: GenerationState
    ): CompiledCodeFragmentData.MethodSignature {
        val typeMapper = state.typeMapper
        val asmSignature = typeMapper.mapSignatureSkipGeneric(methodDescriptor)

        val asmParameters = parameterInfo.parameters.zip(asmSignature.valueParameters).map { (param, sigParam) ->
            getSharedTypeIfApplicable(param, typeMapper) ?: sigParam.asmType
        }

        return CompiledCodeFragmentData.MethodSignature(asmParameters, asmSignature.returnType)
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
}