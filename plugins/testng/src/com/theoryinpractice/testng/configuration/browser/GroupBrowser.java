package com.theoryinpractice.testng.configuration.browser;

import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.util.TestNGUtil;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;

/**
 * @author Hani Suleiman
 *         Date: Jul 21, 2005
 *         Time: 4:29:00 PM
 */
public class GroupBrowser extends BrowseModuleValueActionListener
{
    private TestNGConfigurationEditor editor;

    public GroupBrowser(Project project, TestNGConfigurationEditor editor) {
        super(project);
        this.editor = editor;
    }

    @Override
    protected String showDialog() {
        Module module = editor.getModuleSelector().getModule();        
        TestClassFilter filter = new TestClassFilter(GlobalSearchScope.moduleScope(module), module.getProject(), false);
        PsiClass[] classes = TestNGUtil.getAllTestClasses(filter);
        if(classes.length == 0) {
            Messages.showMessageDialog(getField(), "No tests found in project", "Cannot Browse Groups", Messages.getInformationIcon());
            return null;
        } else {
            return GroupList.showDialog(classes, getField());
        }
    }

}
