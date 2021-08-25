// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testng;

import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationProducer;
import com.theoryinpractice.testng.util.TestNGUtil;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider.JavaTestEntity;
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector;
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil;
import org.jetbrains.kotlin.platform.jvm.JvmPlatformKt;
import org.jetbrains.kotlin.psi.*;

import java.util.List;

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

        KtNamedDeclaration declarationToRun = getDeclarationToRun(element);
        if (declarationToRun == null) return false;
        PsiNamedElement lightElement = CollectionsKt.firstOrNull(LightClassUtilsKt.toLightElements(declarationToRun));
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
        KtNamedDeclaration declarationToRun = getDeclarationToRun(configuration.getSourceElement());
        final PsiNamedElement lightElement = CollectionsKt.firstOrNull(LightClassUtilsKt.toLightElements(declarationToRun));

        // Copied from TestNGInClassConfigurationProducer.onFirstRun()
        if (lightElement instanceof PsiMethod || lightElement instanceof PsiClass) {
            PsiMethod psiMethod;
            PsiClass containingClass;

            if (lightElement instanceof PsiMethod) {
                psiMethod = (PsiMethod)lightElement;
                containingClass = psiMethod.getContainingClass();
            } else {
                psiMethod = null;
                containingClass = (PsiClass)lightElement;
            }

            InheritorChooser inheritorChooser = new InheritorChooser() {
                @Override
                protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
                    ((TestNGConfiguration)context.getConfiguration().getConfiguration()).bePatternConfiguration(classes, method);
                    super.runForClasses(classes, method, context, performRunnable);
                }

                @Override
                protected void runForClass(PsiClass aClass,
                        PsiMethod psiMethod,
                        ConfigurationContext context,
                        Runnable performRunnable) {
                    if (lightElement instanceof PsiMethod) {
                        Project project = psiMethod.getProject();
                        MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
                        ((TestNGConfiguration)context.getConfiguration().getConfiguration()).setMethodConfiguration(methodLocation);
                    } else {
                        ((TestNGConfiguration)context.getConfiguration().getConfiguration()).setClassConfiguration(aClass);
                    }
                    super.runForClass(aClass, psiMethod, context, performRunnable);
                }
            };
            if (inheritorChooser.runMethodInAbstractClass(context,
                                                          startRunnable,
                                                          psiMethod,
                                                          containingClass,
                                                          aClass -> aClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
                                                                    TestNGUtil.hasTest(aClass))) return;
        }

        super.onFirstRun(configuration, context, startRunnable);
    }

    @Nullable
    private static KtNamedDeclaration getDeclarationToRun(@NotNull PsiElement leaf) {
        if (!(leaf.getContainingFile() instanceof KtFile)) return null;
        KtFile ktFile = (KtFile) leaf.getContainingFile();

        KtNamedFunction function = PsiTreeUtil.getParentOfType(leaf, KtNamedFunction.class, false);
        if (function != null) return function;

        KtClass ktClass = PsiTreeUtil.getParentOfType(leaf, KtClass.class, false);
        if (ktClass != null) return ktClass;

        return getClassDeclarationInFile(ktFile);
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

    @Nullable
    static KtClass getClassDeclarationInFile(KtFile ktFile) {
        KtClass tempSingleDeclaration = null;

        for (KtDeclaration ktDeclaration : ktFile.getDeclarations()) {
            if (ktDeclaration instanceof KtClass) {
                KtClass declaration = (KtClass) ktDeclaration;

                if (tempSingleDeclaration == null) {
                    tempSingleDeclaration = declaration;
                }
                else {
                    // There are several class declarations in file
                    return null;
                }
            }
        }

        return tempSingleDeclaration;
    }
}
