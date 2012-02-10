package com.jetbrains.python.packaging;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.psi.PyUtil;

import java.util.List;
import java.util.Properties;

/**
 * @author yole
 */
public class CreateSetupPyAction extends CreateFromTemplateAction {
  private static final String AUTHOR_PROPERTY = "python.packaging.author";
  private static final String EMAIL_PROPERTY = "python.packaging.author.email";

  public CreateSetupPyAction() {
    super(FileTemplateManager.getInstance().getInternalTemplate("Setup Script"));
    getTemplatePresentation().setText("Create setup.py");
  }

  @Override
  public void update(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && PyPackageManager.findSetupPy(module) == null);
  }

  @Override
  public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final AttributesDefaults defaults = new AttributesDefaults("setup.py").withFixedName(true);
    if (project != null) {
      defaults.add("Package_name", project.getName());
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      defaults.add("Author", properties.getOrInit(AUTHOR_PROPERTY, SystemProperties.getUserName()));
      defaults.add("Author_Email", properties.getOrInit(EMAIL_PROPERTY, ""));
    }
    return defaults;
  }

  @Override
  protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module != null) {
      final List<VirtualFile> sourceRoots = PyUtil.getSourceRoots(module);
      if (sourceRoots.size() > 0) {
        return PsiManager.getInstance(module.getProject()).findDirectory(sourceRoots.get(0));
      }
    }
    return super.getTargetDirectory(dataContext, view);
  }

  @Override
  protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement) {
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final Properties properties = dialog.getEnteredProperties();
    final String author = properties.getProperty("Author");
    if (author != null) {
      propertiesComponent.setValue(AUTHOR_PROPERTY, author);
    }
    final String authorEmail = properties.getProperty("Author_Email");
    if (authorEmail != null) {
      propertiesComponent.setValue(EMAIL_PROPERTY, authorEmail);
    }
  }
}
