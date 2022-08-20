// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.util.analyzeInlinedFunctions
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.scopes.receivers.ThisClassReceiver

/**
 * This traversal collects the files containing the sources of
 *   - local functions called by the fragment
 *   - inline functions called by the fragment (and transitively, inline
 *     functions called by those)
 *   - local objects captured by the fragment.
 */
fun gatherProjectFilesDependedOnByFragment(fragment: KtCodeFragment, bindingContext: BindingContext): Set<KtFile> {
    val resolutionFacade = getResolutionFacadeForCodeFragment(fragment)
    val result = mutableSetOf<KtFile>()

    analyzeInlinedFunctions(resolutionFacade, fragment, false, bindingContext).let { (_, files) ->
        for (file in files) {
            result.add(file)
        }
    }

    analyzeCalls(fragment, bindingContext, result)
    return result
}

private fun analyzeCalls(
    fragment: KtCodeFragment,
    bindingContext: BindingContext,
    localFunctions: MutableSet<KtFile>
) {
    val project = fragment.project

    fragment.accept(object : KtTreeVisitorVoid() {
        override fun visitExpression(expression: KtExpression) {
            super.visitExpression(expression)

            val call = bindingContext.get(BindingContext.CALL, expression) ?: return
            val resolvedCall = bindingContext.get(BindingContext.RESOLVED_CALL, call) ?: return

            val descriptor = resolvedCall.resultingDescriptor

            if (descriptor.visibility == DescriptorVisibilities.LOCAL) {
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor) ?: return
                localFunctions.add(declaration.containingFile as KtFile)
            } else if (descriptor.dispatchReceiverParameter?.visibility == DescriptorVisibilities.LOCAL) {
                val thisReceiver = descriptor.dispatchReceiverParameter?.value as? ThisClassReceiver ?: return
                val declaration = DescriptorToSourceUtils.getSourceFromDescriptor(thisReceiver.classDescriptor) ?: return
                localFunctions.add(declaration.containingFile as? KtFile ?: return)
            } // TODO: Extension & Context receivers?
        }
    })
}