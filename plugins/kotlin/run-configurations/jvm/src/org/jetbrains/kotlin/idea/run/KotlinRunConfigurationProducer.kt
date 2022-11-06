// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.ClassUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.findMainOwner
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class KotlinRunConfigurationProducer : LazyRunConfigurationProducer<KotlinRunConfiguration>() {
    override fun setupConfigurationFromContext(
        configuration: KotlinRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val module = location.module?.takeIf { it.platform.isJvm() } ?: return false
        val container = getEntryPointContainer(location) ?: return false
        val startClassFQName = getMainClassJvmName(container) ?: return false

        configuration.setModule(module)
        configuration.runClass = startClassFQName
        configuration.setGeneratedName()

        return true
    }

    private fun getEntryPointContainer(location: Location<*>?): KtDeclarationContainer? {
        val element = location?.psiElement ?: return null
        if (DumbService.getInstance(location.project).isDumb) return null
        return KotlinMainFunctionDetector.getInstance().findMainOwner(element)
    }

    override fun isConfigurationFromContext(configuration: KotlinRunConfiguration, context: ConfigurationContext): Boolean {
        val entryPointContainer = getEntryPointContainer(context.location) ?: return false
        val startClassFQName = getMainClassJvmName(entryPointContainer) ?: return false

        return configuration.runClass == startClassFQName &&
                context.module?.takeIf { it.platform.isJvm() } == configuration.configurationModule?.module
    }

    companion object {
        @Deprecated(
            "Use 'KotlinMainFunctionDetector.findMainOwner()' instead",
            ReplaceWith(
                "KotlinMainFunctionDetector.getInstance().findMainOwner(locationElement)",
                "org.jetbrains.kotlin.idea.base.lineMarkers.run.KotlinMainFunctionDetector",
                "org.jetbrains.kotlin.idea.base.lineMarkers.run.findMainOwner"
            )
        )
        fun getEntryPointContainer(locationElement: PsiElement): KtDeclarationContainer? {
            return KotlinMainFunctionDetector.getInstance().findMainOwner(locationElement)
        }

        @Deprecated(
            "Use 'getStartClassFqName() instead",
            ReplaceWith(
                "getMainClassJvmName(container)",
                "org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer.Companion.getMainClassJvmName"
            )
        )
        fun getStartClassFqName(container: KtDeclarationContainer): String? {
            return getMainClassJvmName(container)
        }

        @ApiStatus.Internal
        fun getMainClassJvmName(container: KtDeclarationContainer): String? = when (container) {
            is KtFile -> container.javaFileFacadeFqName.asString()
            is KtClassOrObject -> {
                if (!container.isValid) {
                    null
                } else if (container is KtObjectDeclaration && container.isCompanion()) {
                    val containerClass = container.getParentOfType<KtClass>(true)
                    containerClass?.toLightClass()?.let { ClassUtil.getJVMClassName(it) }
                } else {
                    container.toLightClass()?.let { ClassUtil.getJVMClassName(it) }
                }
            }
            else -> null
        }
    }

    override fun getConfigurationFactory(): ConfigurationFactory = KotlinRunConfigurationType.instance
}
