package com.intellij.ide.util;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public class TreeClassChooserFactoryImpl extends TreeClassChooserFactory {
  private Project myProject;

  public TreeClassChooserFactoryImpl(final Project project) {
    myProject = project;
  }

  @NotNull
  public TreeClassChooser createWithInnerClassesScopeChooser(String title,
                                                             GlobalSearchScope scope,
                                                             final TreeClassChooser.ClassFilter classFilter,
                                                             PsiClass initialClass) {
    return TreeClassChooserDialog.withInnerClasses(title, myProject, scope, classFilter, initialClass);
  }

  @NotNull
  public TreeClassChooser createNoInnerClassesScopeChooser(String title,
                                                           GlobalSearchScope scope,
                                                           TreeClassChooser.ClassFilter classFilter,
                                                           PsiClass initialClass) {
    return new TreeClassChooserDialog(title, myProject, scope, classFilter, initialClass);
  }

  @NotNull
  public TreeClassChooser createProjectScopeChooser(String title, PsiClass initialClass) {
    return new TreeClassChooserDialog(title, myProject, initialClass);
  }

  @NotNull
  public TreeClassChooser createProjectScopeChooser(String title) {
    return new TreeClassChooserDialog(title, myProject);
  }

  @NotNull
  public TreeClassChooser createAllProjectScopeChooser(String title) {
    return new TreeClassChooserDialog(title, myProject, GlobalSearchScope.allScope(myProject), null, null);
  }

  @NotNull
  public TreeClassChooser createInheritanceClassChooser(String title,
                                                        GlobalSearchScope scope,
                                                        PsiClass base,
                                                        boolean acceptsSelf,
                                                        boolean acceptInner,
                                                        Condition<? super PsiClass> additionalCondition) {
    return new TreeClassChooserDialog(title, myProject, scope, new TreeClassChooserDialog.InheritanceClassFilterImpl(base, acceptsSelf, acceptInner, additionalCondition), null);
  }

  @NotNull
  public TreeFileChooser createFileChooser(String title,
                                           final PsiFile initialFile,
                                           FileType fileType,
                                           TreeFileChooser.PsiFileFilter filter) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, false);
  }

  public
  @NotNull
  TreeFileChooser createFileChooser(@NotNull String title,
                                    @Nullable PsiFile initialFile,
                                    @Nullable FileType fileType,
                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                    boolean disableStructureProviders) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders);
  }

  public void projectOpened() {
  }

  public void projectClosed() {

  }

  @NotNull
  public String getComponentName() {
    return "com.intellij.ide.util.TreeClassFactoryImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
