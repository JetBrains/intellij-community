// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.testng;

import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationProducer;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider;
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider.JavaTestEntity;
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector;
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil;
import org.jetbrains.kotlin.platform.jvm.JvmPlatformKt;
import org.jetbrains.kotlin.psi.KtFile;

public class KotlinTestNgConfigurationProducer extends TestNGConfigurationProducer {
    @Override
    public boolean shouldReplace(@NotNull ConfigurationFromContext self, ConfigurationFromContext other) {
        return other.isProducedBy(TestNGConfigurationProducer.class);
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull TestNGConfiguration configuration, @NotNull ConfigurationContext context) {
        if (isMultipleElementsSelected(context)) {
            return false;
        }
        final RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(getConfigurationType());
        final Location contextLocation = context.getLocation();
        if (contextLocation == null) {
            return false;
        }
        Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
        if (location == null) {
            return false;
        }
        final PsiElement element = location.getPsiElement();

        RunnerAndConfigurationSettings template =
                RunManager.getInstance(location.getProject()).getConfigurationTemplate(getConfigurationFactory());
        final Module predefinedModule = ((TestNGConfiguration) template.getConfiguration()).getConfigurationModule().getModule();
        final String vmParameters =
                predefinedConfiguration instanceof CommonJavaRunConfigurationParameters
                ? ((CommonJavaRunConfigurationParameters) predefinedConfiguration).getVMParameters()
                : null;
        if (vmParameters != null && !Comparing.strEqual(vmParameters, configuration.getVMParameters())) return false;
        if (differentParamSet(configuration, contextLocation)) return false;

        KotlinTestFrameworkProvider.JavaEntity testEntity = TestNgKotlinTestFrameworkProvider.INSTANCE.getJavaEntity(element);
        if (testEntity == null) return false;
        PsiNamedElement lightElement = testEntity.getMethod() != null ? testEntity.getMethod() 
                                                                      : testEntity.getTestClass();
        if (lightElement != null && configuration.isConfiguredByElement(lightElement)) {
            final Module configurationModule = configuration.getConfigurationModule().getModule();
            if (Comparing.equal(location.getModule(), configurationModule)) return true;
            if (Comparing.equal(predefinedModule, configurationModule)) return true;
        }

        return false;
    }

    @Override
    protected boolean setupConfigurationFromContext(
            @NotNull TestNGConfiguration configuration, ConfigurationContext context, @NotNull Ref<PsiElement> sourceElement
    ) {
        // TODO: check TestNG Pattern running first, before method/class (see TestNGInClassConfigurationProducer for logic)
        // TODO: and PsiClassOwner not handled, which is in TestNGInClassConfigurationProducer

        Location location = context.getLocation();
        if (location == null) {
            return false;
        }

        Project project = context.getProject();
        PsiElement leaf = location.getPsiElement();

        if (!ProjectRootsUtil.isInProjectOrLibSource(leaf, false)) {
            return false;
        }

        if (!(leaf.getContainingFile() instanceof KtFile)) {
            return false;
        }

        KtFile ktFile = (KtFile) leaf.getContainingFile();

        if (!JvmPlatformKt.isJvm(TargetPlatformDetector.getPlatform(ktFile))) {
            return false;
        }

        JavaTestEntity testEntity = TestNgKotlinTestFrameworkProvider.INSTANCE.getJavaTestEntity(leaf, true);
        if (testEntity == null) {
            return false;
        }

        return configure(configuration, location, context, project, testEntity.getTestClass(), testEntity.getTestMethod());
    }

    @Override
    public void onFirstRun(ConfigurationFromContext configuration, @NotNull ConfigurationContext context, @NotNull Runnable startRunnable) {
         JavaTestEntity testEntity = TestNgKotlinTestFrameworkProvider.INSTANCE.getJavaTestEntity(configuration.getSourceElement(), true);
         if (testEntity == null) {
             super.onFirstRun(configuration, context, startRunnable);
             return;
         }
        final PsiNamedElement lightElement = testEntity.getTestMethod() != null ? testEntity.getTestMethod() : testEntity.getTestClass();

        ConfigurationFromContext delegate = new ConfigurationFromContext() {
            @Override
            public @NotNull RunnerAndConfigurationSettings getConfigurationSettings() {
                return configuration.getConfigurationSettings();
            }

            @Override
            public void setConfigurationSettings(RunnerAndConfigurationSettings configurationSettings) {
                configuration.setConfigurationSettings(configurationSettings);
            }

            @Override
            public @NotNull PsiElement getSourceElement() {
                return lightElement;
            }
        };
        new AbstractInClassConfigurationProducer<TestNGConfiguration>() {
            @Override
            public @NotNull ConfigurationFactory getConfigurationFactory() {
                return TestNGConfigurationType.getInstance();
            }
        }.onFirstRun(delegate, context, startRunnable);

    }

    private boolean configure(
            TestNGConfiguration configuration, Location location, ConfigurationContext context, Project project,
            @Nullable PsiClass delegate, @Nullable PsiMethod method
    ) {
        if (delegate == null) {
            return false;
        }

        setupConfigurationModule(context, configuration);
        Module originalModule = configuration.getConfigurationModule().getModule();
        configuration.setClassConfiguration(delegate);
        if (method != null) {
            configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
        }
        configuration.restoreOriginalModule(originalModule);
        configuration.setName(configuration.getName());
        JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
        return true;
    }
}
