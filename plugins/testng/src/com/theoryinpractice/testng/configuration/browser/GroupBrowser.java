package com.theoryinpractice.testng.configuration.browser;

import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    @Override
    protected String showDialog() {
      TestClassFilter filter;
      Module module = editor.getModuleSelector().getModule();
      if (module == null) {
        filter = new TestClassFilter(GlobalSearchScope.projectScope(getProject()), getProject(), false);
      }
      else {
        filter = new TestClassFilter(GlobalSearchScope.moduleScope(module), getProject(), false);
      }
      PsiClass[] classes = TestNGUtil.getAllTestClasses(filter);
      if(classes.length == 0) {
        Messages.showMessageDialog(getField(), "No tests found in project", "Cannot Browse Groups", Messages.getInformationIcon());
        return null;
      } else {
        return GroupList.showDialog(classes, getField());
      }
    }

}
