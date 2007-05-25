package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;

/**
 * @author Hani Suleiman Date: Dec 1, 2006 Time: 9:40:24 PM
 */
public class CreatePackageTestAction extends AnAction
{
    private PsiPackage pkg;
    private Module module;

    public void update(AnActionEvent event) {
        pkg = getSelectedPackage(event.getDataContext());
        event.getPresentation().setVisible(pkg != null);
        event.getPresentation().setEnabled(pkg != null);
        if (pkg != null) {
            event.getPresentation().setText("Create \"Tests in '" + pkg.getQualifiedName() + "'\"...");
            module = (Module) event.getDataContext().getData(DataKeys.MODULE.getName());
        } else {
            module = null;
        }
    }

    public void actionPerformed(AnActionEvent event) {
        RunManager runManager = RunManager.getInstance(pkg.getProject());
        ConfigurationFactory[] factory = ApplicationManager.getApplication().getComponent(TestNGConfigurationType.class).getConfigurationFactories();
        RunnerAndConfigurationSettings settings = runManager.createRunConfiguration("", factory[0]);
        final TestNGConfiguration configuration = (TestNGConfiguration) settings.getConfiguration();
        configuration.setPackageConfiguration(module, pkg);
        settings.setName(configuration.getName());
    }

    PsiPackage getSelectedPackage(DataContext context) {
        final PsiElement element = (PsiElement) context.getData(DataKeys.PSI_ELEMENT.getName());
        if (element == null) {
            return null;
        }

        if (element instanceof PsiDirectory) {
            return ((PsiDirectory) element).getPackage();
        }
        return null;
    }
}
