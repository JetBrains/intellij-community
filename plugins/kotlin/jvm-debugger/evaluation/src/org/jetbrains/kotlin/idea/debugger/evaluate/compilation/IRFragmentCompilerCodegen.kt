/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.backend.jvm.FacadeClassSourceShimForFragmentCompilation
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensionsImpl
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.codegen.CodegenFactory
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.debugger.evaluate.DebuggerFieldPropertyDescriptor
import org.jetbrains.kotlin.idea.debugger.evaluate.EvaluationStatus
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.isEvaluationEntryPoint
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmDescriptorMangler
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.util.NameProvider
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi2ir.generators.fragments.EvaluatorFragmentInfo
import org.jetbrains.kotlin.psi2ir.generators.fragments.EvaluatorFragmentParameterInfo
import org.jetbrains.kotlin.psi2ir.generators.fragments.FragmentCompilerSymbolTableDecorator
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_CODE


class IRFragmentCompilerCodegen : FragmentCompilerCodegen {

    override fun initCodegen(
        classDescriptor: ClassDescriptor,
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo
    ) {
        // NO-OP
    }

    override fun cleanupCodegen() {
        // NO-OP
    }

    override fun configureCompiler(compilerConfiguration: CompilerConfiguration) {
        // TODO: Do not understand the implications of DO_NOT_CLEAR_BINDING_CONTEXT,
        //       but enforced by assertions in JvmIrCodegen
        compilerConfiguration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
        compilerConfiguration.put(JVMConfigurationKeys.IR, true)
    }

    override fun configureGenerationState(
        builder: GenerationState.Builder,
        bindingContext: BindingContext,
        compilerConfiguration: CompilerConfiguration,
        classDescriptor: ClassDescriptor,
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo
    ) {
        builder.isIrBackend(true)
        builder.codegenFactory(
            codegenFactory(
                bindingContext,
                compilerConfiguration,
                classDescriptor,
                methodDescriptor,
                parameterInfo
            )
        )
    }

    private fun codegenFactory(
        bindingContext: BindingContext,
        compilerConfiguration: CompilerConfiguration,
        classDescriptor: ClassDescriptor,
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo
    ): CodegenFactory {
        val mangler = JvmDescriptorMangler(MainFunctionDetector(bindingContext, compilerConfiguration.languageVersionSettings))
        val evaluatorFragmentInfo = EvaluatorFragmentInfo(
            classDescriptor,
            methodDescriptor,
            parameterInfo.parameters.map { EvaluatorFragmentParameterInfo(it.targetDescriptor, it.isLValue) }
        )
        return JvmIrCodegenFactory(
            configuration = compilerConfiguration,
            phaseConfig = null,
            externalMangler = mangler,
            externalSymbolTable = FragmentCompilerSymbolTableDecorator(
                JvmIdSignatureDescriptor(mangler),
                IrFactoryImpl,
                evaluatorFragmentInfo,
                NameProvider.DEFAULT,
            ),
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
            evaluatorFragmentInfoForPsi2Ir = evaluatorFragmentInfo,
            JvmIrCodegenFactory.IdeCodegenSettings(shouldStubAndNotLinkUnboundSymbols = true)
        )
    }

    override fun computeFragmentParameters(
        executionContext: ExecutionContext,
        codeFragment: KtCodeFragment,
        bindingContext: BindingContext,
        status: EvaluationStatus
    ): CodeFragmentParameterInfo =
        CodeFragmentParameterAnalyzer(executionContext, codeFragment, bindingContext, status).analyze().let { analysis ->
            // Local functions do not exist as run-time values on the IR backend: they are static functions.
            CodeFragmentParameterInfo(
                analysis.parameters.filter { it.kind != CodeFragmentParameter.Kind.LOCAL_FUNCTION },
                analysis.crossingBounds
            )
        }

    override fun extractResult(
        methodDescriptor: FunctionDescriptor,
        parameterInfo: CodeFragmentParameterInfo,
        generationState: GenerationState
    ): CodeFragmentCompiler.CompilationResult {
        val classes: List<ClassToLoad> = collectGeneratedClasses(generationState)
        val fragmentClass = classes.single { it.className == GENERATED_CLASS_NAME }
        val methodSignature = getMethodSignature(fragmentClass)

        val newCaptures: List<CodeFragmentParameter.Smart> = generationState.newFragmentCaptureParameters.map { (name, type, target) ->
            val kind = when {
                name == "<this>" -> CodeFragmentParameter.Kind.DISPATCH_RECEIVER
                target is LocalVariableDescriptor && target.isDelegated -> CodeFragmentParameter.Kind.DELEGATED
                else -> CodeFragmentParameter.Kind.ORDINARY
            }
            val dumb = CodeFragmentParameter.Dumb(kind, name)
            CodeFragmentParameter.Smart(dumb, type, target)
        }

        val processedOldCaptures: List<CodeFragmentParameter.Smart> = parameterInfo.parameters.map {
            val target = it.targetDescriptor
            val (newName, newDebugName) = when {
                target is LocalVariableDescriptor && target.isDelegated -> {
                    val mangledName = it.name + "\$delegate"
                    mangledName to mangledName
                }
                it.name == "" ->
                    it.name to it.debugString
                else ->
                    it.name to it.name
            }
            val dumb = CodeFragmentParameter.Dumb(it.dumb.kind, newName, newDebugName)
            CodeFragmentParameter.Smart(dumb, it.targetType, it.targetDescriptor)
        }

        val newParameterInfo =
            CodeFragmentParameterInfo(
                processedOldCaptures + newCaptures,
                parameterInfo.crossingBounds
            )

        return CodeFragmentCompiler.CompilationResult(classes, newParameterInfo, mapOf(), methodSignature)
    }

    private fun collectGeneratedClasses(generationState: GenerationState): List<ClassToLoad> {
        return generationState.factory.asList()
            .filterCodeFragmentClassFiles()
            .map {
                ClassToLoad(it.internalClassName, it.relativePath, it.asByteArray())
            }
    }

    private fun getMethodSignature(
        fragmentClass: ClassToLoad,
    ): CompiledCodeFragmentData.MethodSignature {
        val parameters: MutableList<Type> = mutableListOf()
        var returnType: Type? = null

        ClassReader(fragmentClass.bytes).accept(object : ClassVisitor(Opcodes.ASM7) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                if (name != null && isEvaluationEntryPoint(name)) {
                    Type.getArgumentTypes(descriptor).forEach { parameters.add(it) }
                    returnType = Type.getReturnType(descriptor)
                }
                return null
            }
        }, SKIP_CODE)

        return CompiledCodeFragmentData.MethodSignature(parameters, returnType!!)
    }
}

