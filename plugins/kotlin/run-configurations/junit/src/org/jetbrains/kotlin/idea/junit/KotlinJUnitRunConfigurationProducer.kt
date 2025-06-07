// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.*
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.junit.*
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.run.forceGradleRunnerInMPP
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
class KotlinJUnitRunConfigurationProducer private constructor(): LazyRunConfigurationProducer<JUnitConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return JUnitConfigurationType.getInstance().configurationFactories[0]
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isProducedBy(JUnitConfigurationProducer::class.java)
                || other.isProducedBy(AbstractPatternBasedConfigurationProducer::class.java)
    }

    private fun isAvailableInMpp(context: ConfigurationContext): Boolean {
        val module = context.module
        return module == null || !module.isNewMultiPlatformModule || !forceGradleRunnerInMPP()
    }

    override fun isConfigurationFromContext(
        configuration: JUnitConfiguration,
        context: ConfigurationContext
    ): Boolean {
        if (getInstance(PatternConfigurationProducer::class.java).isMultipleElementsSelected(context) || !isAvailableInMpp(context)) {
            return false
        }

        if (JUnitConfiguration.TEST_CLASS != configuration.testType && 
            JUnitConfiguration.TEST_METHOD != configuration.testType) {
            return false
        }

        val element = context.location?.psiElement ?: return false
        val javaEntity = JunitKotlinTestFrameworkProvider.getInstance().getJavaEntity(element) ?: return false

        val testObject = configuration.testObject
        if (!testObject.isConfiguredByElement(configuration, javaEntity.testClass, javaEntity.method, null, null)) {
            return false
        }

        val nodeIds = UniqueIdConfigurationProducer.getNodeIds(context)
        if (!nodeIds.isNullOrEmpty()) {
            return false
        }
        
        return settingsMatchTemplate(configuration, context)
    }

    // copied from JUnitConfigurationProducer in IDEA
    private fun settingsMatchTemplate(configuration: JUnitConfiguration, context: ConfigurationContext): Boolean {
        val predefinedConfiguration = context.getOriginalConfiguration(JUnitConfigurationType.getInstance())

        val vmParameters = (predefinedConfiguration as? CommonJavaRunConfigurationParameters)?.vmParameters
        if (vmParameters != null && configuration.vmParameters != vmParameters) return false

        val template = RunManager.getInstance(configuration.project).getConfigurationTemplate(configurationFactory)
        val predefinedModule = (template.configuration as ModuleBasedConfiguration<*, *>).configurationModule.module
        val configurationModule = configuration.configurationModule.module
        return configurationModule == context.location?.module?.takeIf { it.platform.isJvm() } || configurationModule == predefinedModule
    }

    public override fun setupConfigurationFromContext(
        configuration: JUnitConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        if (DumbService.getInstance(context.project).isDumb) return false

        if (getInstance(PatternConfigurationProducer::class.java).isMultipleElementsSelected(context) || !isAvailableInMpp(context)) {
            return false
        }

        val location = context.location ?: return false
        val element = location.psiElement
        context.module?.takeIf { it.platform.isJvm() } ?: return false

        if (!RootKindFilter.projectAndLibrarySources.matches(element) || element.containingFile !is KtFile) {
            return false
        }

        val nodeIds = UniqueIdConfigurationProducer.getNodeIds(context)
        if (!nodeIds.isNullOrEmpty()) {
            return false
        }
        
        val testEntity = JunitKotlinTestFrameworkProvider.getInstance().getJavaTestEntity(element, checkMethod = true) ?: return false

        val originalModule = configuration.configurationModule.module
        val testMethod = testEntity.testMethod
        if (testMethod != null) {
            configuration.beMethodConfiguration(PsiLocation.fromPsiElement(testMethod))
        } else {
            configuration.beClassConfiguration(testEntity.testClass)
        }

        configuration.restoreOriginalModule(originalModule)
        JavaRunConfigurationExtensionManager.instance.extendCreatedConfiguration(configuration, location)
        return true
    }

    override fun onFirstRun(fromContext: ConfigurationFromContext, context: ConfigurationContext, performRunnable: Runnable) {
        val testEntity =
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable {
                    runReadAction {
                        JunitKotlinTestFrameworkProvider.getInstance().getJavaTestEntity(fromContext.sourceElement, checkMethod = true)
                    }
                },
                KotlinJUnitBundle.message("progress.text.detect.test.framework"),
                true,
                context.project
            ) ?: return super.onFirstRun(fromContext, context, performRunnable)

        val sourceElement = testEntity.testMethod ?: testEntity.testClass

        val contextWithLightElement = createDelegatingContextWithLightElement(fromContext, sourceElement)
        // TODO: use TestClassConfigurationProducer when constructor becomes public
        return object : AbstractInClassConfigurationProducer<JUnitConfiguration>() {
            override fun getConfigurationFactory(): ConfigurationFactory = JUnitConfigurationType.getInstance().factory
        }.onFirstRun(contextWithLightElement, context, performRunnable)
    }

    private fun createDelegatingContextWithLightElement(
        fromContext: ConfigurationFromContext,
        lightElement: PsiMember
    ): ConfigurationFromContext {
        return object : ConfigurationFromContext() {
            override fun getConfigurationSettings() = fromContext.configurationSettings

            override fun setConfigurationSettings(configurationSettings: RunnerAndConfigurationSettings) {
                fromContext.configurationSettings = configurationSettings
            }

            override fun getSourceElement() = lightElement
        }
    }
}