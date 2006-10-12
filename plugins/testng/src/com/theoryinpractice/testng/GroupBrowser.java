package com.theoryinpractice.testng;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.util.TestNGUtil;

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
        Project project = editor.getModuleSelector().getProject();
        //TODO narrow scope according to what's chosen in editor
        PsiClass[] classes = TestNGUtil.getAllTestClasses(new TestClassFilter(GlobalSearchScope.projectScope(project), project, false));
        if(classes.length == 0) {
            Messages.showMessageDialog(getField(), "No tests found in project", "Cannot Browse Groups", Messages.getInformationIcon());
            return null;
        } else {
            return GroupList.showDialog(classes, getField());
        }
    }

}
