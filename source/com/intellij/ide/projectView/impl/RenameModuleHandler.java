package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
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

/**
 * @author dsl
 */
public class RenameModuleHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.actions.RenameModuleHandler");
  static final RenameModuleHandler INSTANCE = new RenameModuleHandler();

  private RenameModuleHandler() {

  }

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Module module = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    return module != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.assertTrue(false);
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(dataContext != null);
    final Module module = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    LOG.assertTrue(module != null);
    Messages.showInputDialog(project,
                             "Enter new module name",
                             "Rename Module",
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
      return true;
    }

    public boolean canClose(final String inputString) {
      final ModifiableModuleModel modifiableModel = ModuleManager.getInstance(myProject).getModifiableModel();
      try {
        modifiableModel.renameModule(myModule, inputString);
      }
      catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
        Messages.showErrorDialog(myProject, "Module named '" + inputString + "' already exists",
                                 "Rename Module");
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
                                Messages.showErrorDialog(myProject, "Renaming module '" + myModule.getName() + "' to '" +
                                                                    inputString +
                                                                    "'\n" +
                                                                    "will introduce circular dependency between \n" +
                                                                    "modules '" + e.getModuleName1() + "' and '" +
                                                                    e.getModuleName2() +
                                                                    "'",
                                                         "Rename Module");
                              }
                            });
                modifiableModel.dispose();
                success.set(Boolean.FALSE);
                return;
              }
            }
          });
        }
      }, "Renaming module " + myModule.getName(), null);
      return success.get().booleanValue();
    }
  }

}
