/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 2, 2005
 * Time: 12:10:47 AM
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestData;

import javax.swing.*;

public class TestNGConfigurationType implements LocatableConfigurationType
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

    public static TestNGConfigurationType getInstance() {
        return ApplicationManager.getApplication().getComponent(TestNGConfigurationType.class);
    }

    public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
        return new TestNGConfigurationProducer().createProducer(location, null).getConfiguration();
    }

    public boolean isConfigurationByElement(RunConfiguration runConfiguration, Project project, PsiElement element) {
        TestNGConfiguration config = (TestNGConfiguration) runConfiguration;
        TestData testobject = config.getPersistantData();
        if (testobject == null)
            return false;
        else
            return testobject.isConfiguredByElement(element);
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