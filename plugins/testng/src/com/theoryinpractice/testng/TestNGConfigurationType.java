/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 2, 2005
 * Time: 12:10:47 AM
 */
package com.theoryinpractice.testng;

import javax.swing.*;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.inspection.JUnitConvertTool;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.util.TestNGUtil;

public class TestNGConfigurationType implements LocatableConfigurationType, InspectionToolProvider
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
    private static final Icon ICON = IconLoader.getIcon("/resources/testng.gif");

    private final ConfigurationFactory myFactory;

    public TestNGConfigurationType() {

        myFactory = new ConfigurationFactory(this)
        {
            @Override
            public RunConfiguration createTemplateConfiguration(Project project) {
                LOGGER.info("Create TestNG Template Configuration");
                return new TestNGConfiguration("", project, this);
            }
        };
    }

    public Class[] getInspectionClasses() {
        return new Class[] {JUnitConvertTool.class};
    }

    public static TestNGConfigurationType getInstance() {
        return ApplicationManager.getApplication().getComponent(TestNGConfigurationType.class);
    }

    public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
        PsiElement element = location.getPsiElement();
        PsiClass psiClass;
        if (element instanceof PsiClass) {
            psiClass = (PsiClass) element;
        } else {
            psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        }
        if (psiClass == null) return null;
        if (!ExecutionUtil.isRunnableClass(psiClass)) return null;
        if (!TestNGUtil.hasTest(psiClass)) return null;
        final Project project = location.getProject();
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project).createRunConfiguration("", getConfigurationFactories()[0]);
        final TestNGConfiguration configuration = (TestNGConfiguration) settings.getConfiguration();
        configuration.setClassConfiguration(psiClass);
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null && TestNGUtil.hasTest(method)) {
            configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
        }
        settings.setName(configuration.getName());
        return settings;
    }

    public boolean isConfigurationByElement(RunConfiguration runConfiguration, Project project, PsiElement element) {
        TestNGConfiguration config = (TestNGConfiguration) runConfiguration;
        TestData testobject = config.getPersistantData();
        if (testobject == null)
            return false;
        else
            return testobject.isConfiguredByElement(config, element);
    }

    public String getDisplayName() {
        return "TestNG";
    }

    public String getConfigurationTypeDescription() {
        return "TestNG Configuration";
    }

    public Icon getIcon() {
        return ICON;
    }

    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[] {myFactory};
    }

    public String getComponentName() {
        return "TestNG";
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

}