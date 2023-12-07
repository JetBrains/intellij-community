// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.descriptors.utils.collectReachableInlineDelegatedPropertyAccessors
import org.jetbrains.kotlin.analysis.api.descriptors.utils.getInlineFunctionAnalyzer
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.util.expectedDeclarationIfAny
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.scopes.receivers.*

/**
 * This traversal collects the files containing the sources of
 *   - local functions called by the fragment
 *   - inline functions called by the fragment (and transitively, inline
 *     functions called by those)
 *   - local objects captured by the fragment.
 *   - local classes constructed by the fragment.
 */
fun gatherProjectFilesDependedOnByFragment(fragment: KtCodeFragment, bindingContext: BindingContext): Set<KtFile> {
    val result = mutableSetOf<KtFile>()
    analyze(fragment) {
        result += with(getInlineFunctionAnalyzer(false)) {
            analyze(fragment)
            allFiles()
        }
        analyzeCalls(fragment, bindingContext, result)
        result.collectReachableInlineDelegatedPropertyAccessors()
    }
    return result
}

private fun analyzeCalls(
    fragment: KtCodeFragment,
    bindingContext: BindingContext,
    files: MutableSet<KtFile>
) {
    val project = fragment.project

    fragment.accept(object : KtTreeVisitorVoid() {
        override fun visitExpression(expression: KtExpression) {
            super.visitExpression(expression)

            val call = bindingContext.get(BindingContext.CALL, expression) ?: return
            val resolvedCall = bindingContext.get(BindingContext.RESOLVED_CALL, call) ?: return

            val descriptor = resolvedCall.resultingDescriptor

            fun processClassReceiver(receiver: ReceiverValue?) {
                val declarationDescriptor = (receiver as? ImplicitReceiver)?.declarationDescriptor ?: return
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, declarationDescriptor) ?: return
                files.add(declaration.containingFile as KtFile)
            }

            // If the implicit receiver of a call is a context class receiver,
            // we have to add the containing file of the class that accepts this
            // receiver as a dependency, because context class receivers are put
            // on a stack with the 'getfield' instruction, and the fragment compiler
            // will need the generated synthetic fields to generate the bytecode correctly.
            // Consider the example:
            // class Ctx { fun foo() = 1 }
            //
            // context(Ctx)
            // class A {
            //     fun bar() = foo()
            // }
            //
            // The simplified bytecode of the 'bar' function will consist of
            // these opcodes as of Kotlin 1.8:
            //   0: aload_0
            //   1: getfield      #22  // Field contextReceiverField0:LCtx;
            //   4: invokevirtual #31  // Method Ctx.foo:()I
            //   7: pop
            //   8: return

            with(resolvedCall) {
                dispatchReceiver.takeIf {
                    it is ContextClassReceiver || it is ImplicitClassReceiver && it.classDescriptor.visibility == DescriptorVisibilities.LOCAL
                }?.let { processClassReceiver(it) }
                contextReceivers.forEach(::processClassReceiver)
            }

            if (descriptor is ReceiverParameterDescriptor) {
                processClassReceiver(descriptor.value)
            }

            if ((descriptor as? MemberDescriptor)?.isActual == true) {
                val actualDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
                val expectedDeclaration = (actualDeclaration as? KtDeclaration)?.expectedDeclarationIfAny()
                listOfNotNull(actualDeclaration, expectedDeclaration).forEach { files.add(it.containingFile as KtFile) }
            }

            if (descriptor.visibility == DescriptorVisibilities.LOCAL) {
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor) ?: return
                files.add(declaration.containingFile as KtFile)
            } else if (descriptor.dispatchReceiverParameter?.visibility == DescriptorVisibilities.LOCAL) {
                val thisReceiver = descriptor.dispatchReceiverParameter?.value as? ThisClassReceiver ?: return
                val declaration = DescriptorToSourceUtils.getSourceFromDescriptor(thisReceiver.classDescriptor) ?: return
                files.add(declaration.containingFile as? KtFile ?: return)
            } else if (descriptor.extensionReceiverParameter?.visibility == DescriptorVisibilities.LOCAL) {
                val thisReceiver = descriptor.extensionReceiverParameter?.value as? ExtensionReceiver ?: return
                val declarationDescriptor = thisReceiver.type.constructor.declarationDescriptor ?: return
                if ((declarationDescriptor as? DeclarationDescriptorWithVisibility)?.visibility != DescriptorVisibilities.LOCAL) return
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, declarationDescriptor) ?: return
                files.add(declaration.containingFile as? KtFile ?: return)
            } else if ((descriptor as? ClassConstructorDescriptor)?.constructedClass?.visibility == DescriptorVisibilities.LOCAL) {
                val declaration = DescriptorToSourceUtils.getSourceFromDescriptor((descriptor).constructedClass) ?: return
                files.add(declaration.containingFile as? KtFile ?: return)
            }
        }
    })
}