package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ConfigurationUtil;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

public abstract class ClassBrowser extends BrowseModuleValueActionListener {
  private final String myTitle;

  public ClassBrowser(final Project project, final String title) {
    super(project);
    myTitle = title;
  }

  protected String showDialog() {
    final TreeClassChooserDialog.ClassFilterWithScope classFilter;
    try {
      classFilter = getFilter();
    }
    catch (NoFilterException e) {
      final MessagesEx.MessageInfo info = e.getMessageInfo();
      info.showNow();
      return null;
    }
    final TreeClassChooserDialog dialog = TreeClassChooserDialog.
      withInnerClasses(myTitle, getProject(), classFilter.getScope(), classFilter, null);
    configureDialog(dialog);
    dialog.show();
    final PsiClass psiClass = dialog.getSelectedClass();
    if (psiClass == null) return null;
    onClassChoosen(psiClass);
    return ExecutionUtil.getRuntimeQualifiedName(psiClass);
  }

  protected abstract TreeClassChooserDialog.ClassFilterWithScope getFilter() throws NoFilterException;

  protected void onClassChoosen(final PsiClass psiClass) { }

  private void configureDialog(final TreeClassChooserDialog dialog) {
    final String className = getText();
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) return;
    final PsiDirectory directory = psiClass.getContainingFile().getContainingDirectory();
    if (directory != null) dialog.selectDirectory(directory);
    dialog.selectClass(psiClass);
  }

  protected abstract PsiClass findClass(String className);

  public static ClassBrowser createApplicationClassBrowser(final Project project,
                                                           final ConfigurationModuleSelector moduleSelector) {
    final TreeClassChooserDialog.ClassFilter applicationClass = new TreeClassChooserDialog.ClassFilter() {
      public boolean isAccepted(final PsiClass aClass) {
        return ConfigurationUtil.MAIN_CLASS.value(aClass) && ApplicationConfigurationType.findMainMethod(aClass) != null;
      }
    };
    return new MainClassBrowser(project, moduleSelector, "Choose Main Class"){
      protected TreeClassChooserDialog.ClassFilter createFilter(final Module module) {
        return applicationClass;
      }
    };
  }

  public static ClassBrowser createAppletClassBrowser(final Project project,
                                                      final ConfigurationModuleSelector moduleSelector) {
    return new MainClassBrowser(project, moduleSelector, "Choose Applet Class") {
      protected TreeClassChooserDialog.ClassFilter createFilter(final Module module) {
        final GlobalSearchScope scope = module != null ?
                                  GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) :
                                  GlobalSearchScope.allScope(myProject);
        final PsiClass appletClass = PsiManager.getInstance(project).findClass("java.applet.Applet", scope);
        return new TreeClassChooserDialog.InheritanceClassFilter(appletClass, false, false,
                                                                 ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS);
      }
    };
  }

  private static abstract class MainClassBrowser extends ClassBrowser {
    protected final Project myProject;
    private final ConfigurationModuleSelector myModuleSelector;

    public MainClassBrowser(final Project project,
                            final ConfigurationModuleSelector moduleSelector,
                            final String title) {
      super(project, title);
      myProject = project;
      myModuleSelector = moduleSelector;
    }

    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    protected TreeClassChooserDialog.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      final GlobalSearchScope scope;
      if (module == null) scope = GlobalSearchScope.projectScope(myProject);
      else scope = GlobalSearchScope.moduleWithDependenciesScope(module);
      final TreeClassChooserDialog.ClassFilter filter = createFilter(module);
      return new TreeClassChooserDialog.ClassFilterWithScope() {
        public GlobalSearchScope getScope() {
          return scope;
        }

        public boolean isAccepted(final PsiClass aClass) {
          return (filter == null || filter.isAccepted(aClass));
        }
      };
    }

    protected TreeClassChooserDialog.ClassFilter createFilter(final Module module) { return null; }
  }

  public static class NoFilterException extends Exception {
    private MessagesEx.MessageInfo myMessageInfo;

    public NoFilterException(final MessagesEx.MessageInfo messageInfo) {
      super(messageInfo.getMessage());
      myMessageInfo = messageInfo;
    }

    public MessagesEx.MessageInfo getMessageInfo() {
      return myMessageInfo;
    }

    public static NoFilterException noJUnitInModule(final Module module) {
      return new NoFilterException(new MessagesEx.MessageInfo(
        module.getProject(),
        "JUnit not found in module \"" + module.getName() + "\"",
        "Can't Browse TestCase Inheritors"));
    }

    public static NoFilterException moduleDoesntExist(final ConfigurationModuleSelector moduleSelector) {
      final Project project = moduleSelector.getProject();
      return new NoFilterException(new MessagesEx.MessageInfo(
        project,
        "Module \"" + moduleSelector.getModuleName() + "\" does not exist in project \"" + project.getName() + "\"",
        "Can't Browse TestCase Inheritors"));
    }
  }
}
