package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class RenameModuleHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.actions.RenameModuleHandler");

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Module module = DataKeys.MODULE_CONTEXT.getData(dataContext);
    return module != null;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.assertTrue(false);
  }

  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(dataContext != null);
    final Module module = DataKeys.MODULE_CONTEXT.getData(dataContext);
    LOG.assertTrue(module != null);
    Messages.showInputDialog(project,
                             IdeBundle.message("prompt.enter.new.module.name"),
                             IdeBundle.message("title.rename.module"),
                             Messages.getQuestionIcon(),
                             module.getName(),
                             new MyInputValidator(project, module));
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final Module myModule;

    public MyInputValidator(Project project, Module module) {
      myProject = project;
      myModule = module;
    }

    public boolean checkInput(String inputString) {
      return inputString != null && inputString.length() > 0;
    }

    public boolean canClose(final String inputString) {
      final ModifiableModuleModel modifiableModel = ModuleManager.getInstance(myProject).getModifiableModel();
      try {
        modifiableModel.renameModule(myModule, inputString);
      }
      catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
        Messages.showErrorDialog(myProject, IdeBundle.message("error.module.already.exists", inputString),
                                 IdeBundle.message("title.rename.module"));
        return false;
      }
      final Ref<Boolean> success = Ref.create(Boolean.TRUE);
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                modifiableModel.commit();
              }
              catch (final ModuleCircularDependencyException e) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                              public void run() {
                                Messages.showErrorDialog(myProject, IdeBundle.message(
                                  "error.renaming.module.will.introduce.circular.dependency", myModule.getName(), inputString,
                                  e.getModuleName1(), e.getModuleName2()),
                                                         IdeBundle.message("title.rename.module"));
                              }
                            });
                modifiableModel.dispose();
                success.set(Boolean.FALSE);
              }
            }
          });
        }
      }, IdeBundle.message("command.renaming.module", myModule.getName()), null);
      return success.get().booleanValue();
    }
  }

}
