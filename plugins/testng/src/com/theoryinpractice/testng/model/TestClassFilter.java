package com.theoryinpractice.testng.model;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.openapi.project.Project;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.theoryinpractice.testng.util.TestNGUtil;

/**
 * @author Hani Suleiman
 *         Date: Jul 21, 2005
 *         Time: 9:03:06 PM
 */
public class TestClassFilter implements TreeClassChooser.ClassFilterWithScope
{
    private final GlobalSearchScope scope;
    private final Project project;
    private final boolean includeConfig;
    
    public TestClassFilter(GlobalSearchScope scope, Project project, boolean includeConfig) {
        this.scope = scope;
        this.project = project;
        this.includeConfig = includeConfig;
    }

    public TestClassFilter intersectionWith(GlobalSearchScope scope) {
        return new TestClassFilter(this.scope.intersectWith(scope), project, includeConfig);
    }

    public boolean isAccepted(PsiClass psiClass) {
        if(!ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(psiClass)) return false;
        //PsiManager manager = PsiManager.getInstance(project);
        //if(manager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) return true;
        boolean hasTest = TestNGUtil.hasTest(psiClass);
        if(hasTest) return true;
        return includeConfig && TestNGUtil.hasConfig(psiClass);
    }

    public Project getProject() {
        return project;
    }

    public GlobalSearchScope getScope() {
        return scope;
    }
}
