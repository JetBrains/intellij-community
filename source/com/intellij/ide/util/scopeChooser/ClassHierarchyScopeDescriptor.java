/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class ClassHierarchyScopeDescriptor extends ScopeDescriptor {
  private SearchScope myCachedScope;
  private final Project myProject;

  public ClassHierarchyScopeDescriptor(final Project project) {
    super(null);
    myProject = project;
  }

  public String getDisplay() {
    return IdeBundle.message("scope.class.hierarchy");
  }

  @Nullable
  public SearchScope getScope() {
    if (myCachedScope == null) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(IdeBundle.message("prompt.choose.base.class.of.the.hierarchy"));

      chooser.showDialog();

      PsiClass aClass = chooser.getSelectedClass();
      if (aClass == null) return null;

      List<PsiElement> classesToSearch = new LinkedList<PsiElement>();
      classesToSearch.add(aClass);

      classesToSearch.addAll(ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).findAll());

      myCachedScope = new LocalSearchScope(classesToSearch.toArray(new PsiElement[classesToSearch.size()]),
                                           IdeBundle.message("scope.hierarchy", ClassPresentationUtil.getNameForClass(aClass, true)));
    }

    return myCachedScope;
  }
}