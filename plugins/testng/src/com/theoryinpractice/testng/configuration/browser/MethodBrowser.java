package com.theoryinpractice.testng.configuration.browser;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;

/**
 * @author Hani Suleiman
 *         Date: Jul 21, 2005
 *         Time: 4:29:00 PM
 */
public class MethodBrowser extends BrowseModuleValueActionListener
{
    private TestNGConfigurationEditor editor;

    public MethodBrowser(Project project, TestNGConfigurationEditor editor) {
        super(project);
        this.editor = editor;
    }

    @Override
    protected String showDialog() {
        String className = editor.getClassName();
        if(className.trim().length() == 0) {
            Messages.showMessageDialog(getField(), "Set class name first", "Cannot Browse Methods", Messages.getInformationIcon());
            return null;
        }
        PsiClass psiclass = editor.getModuleSelector().findClass(className);
        if(psiclass == null) {
            Messages.showMessageDialog(getField(), "Class " + className + " does not exist", "Cannot Browse Methods", Messages.getInformationIcon());
            return null;
        } else {
            PsiMethod psimethod = MethodList.showDialog(psiclass, getField());
            return psimethod == null ? null : psimethod.getName();
        }
    }

}
