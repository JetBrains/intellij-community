package com.theoryinpractice.testng.configuration.browser;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.module.Module;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import com.theoryinpractice.testng.MessageInfoException;

/**
 * @author Hani Suleiman
 *         Date: Jul 20, 2005
 *         Time: 2:02:00 PM
 */
public class TestClassBrowser extends BrowseModuleValueActionListener
{
    private TestNGConfigurationEditor editor;

    public TestClassBrowser(Project project, TestNGConfigurationEditor editor)
    {
        super(project);
        this.editor = editor;
    }

    @Override
    protected String showDialog()
    {
        com.intellij.ide.util.TreeClassChooser.ClassFilterWithScope filter;
        try
        {
            filter = getFilter();
        }
        catch(MessageInfoException e)
        {
            com.intellij.openapi.ui.ex.MessagesEx.MessageInfo message = e.getMessageInfo();
            message.showNow();
            return null;
        }
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject()).createWithInnerClassesScopeChooser("Choose Test Class", filter.getScope(), filter, null);
        init(chooser);
        chooser.showDialog();
        PsiClass psiclass = chooser.getSelectedClass();
        if(psiclass == null)
        {
            return null;
        } else
        {
            onClassChoosen(psiclass);
            return ExecutionUtil.getRuntimeQualifiedName(psiclass);
        }
    }

    protected void onClassChoosen(PsiClass psiClass)
    {
    }

    protected PsiClass findClass(String className)
    {
        return editor.getModuleSelector().findClass(className);
    }

    protected TreeClassChooser.ClassFilterWithScope getFilter() throws MessageInfoException
    {
        TestNGConfiguration config = new TestNGConfiguration("<no-name>", getProject(), TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
        editor.applyEditorTo(config);
        GlobalSearchScope scope = getSearchScope(config.getModules());
        if(scope == null) {
            throw new MessageInfoException(new MessagesEx.MessageInfo(getProject(), "No classes found in project", "Can't Browse Tests"));
        }
        return new TestClassFilter(scope, getProject(), false);
    }

    private GlobalSearchScope getSearchScope(Module[] modules) {
        if(modules == null || modules.length == 0) return null;
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(modules[0]);
        for(int i = 1; i < modules.length; i++) {
            scope.uniteWith(GlobalSearchScope.moduleWithDependenciesScope(modules[i]));
        }
        return scope;
    }

    private void init(TreeClassChooser chooser)
    {
        String s = getText();
        PsiClass psiclass = findClass(s);
        if(psiclass == null)
            return;
        com.intellij.psi.PsiDirectory psidirectory = psiclass.getContainingFile().getContainingDirectory();
        if(psidirectory != null)
            chooser.selectDirectory(psidirectory);
        chooser.selectClass(psiclass);
    }
}
