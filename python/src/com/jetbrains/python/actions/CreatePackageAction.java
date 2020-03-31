// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateDirectoryOrPackageHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

public final class CreatePackageAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreatePackageAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    final SmartPsiElementPointer<PsiDirectory> directoryPointer = pointerManager.createSmartPsiElementPointer(directory);
    final CreateDirectoryOrPackageHandler validator = new CreateDirectoryOrPackageHandler(project, directory, false, ".") {
      @Override
      protected void createDirectories(String subDirName) {
        super.createDirectories(subDirName);
        PsiFileSystemItem element = getCreatedElement();
        final PsiDirectory restoredDirectory = directoryPointer.getElement();
        if (element instanceof PsiDirectory && restoredDirectory != null) {
          createInitPyInHierarchy((PsiDirectory)element, restoredDirectory);
        }
      }
    };

    Consumer<PsiFileSystemItem> consumer = item -> {
      if (item != null) {
        view.selectElement(item);
      }
    };

    if (Experiments.getInstance().isFeatureEnabled("show.create.new.element.in.popup")) {
      JBPopup popup = createLightWeightPopup(validator, consumer);
      if (project != null) {
        popup.showCenteredInCurrentWindow(project);
      }
      else {
        popup.showInFocusCenter();
      }
    }
    else {
      Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.package.name"), IdeBundle.message("title.new.package"), Messages.getQuestionIcon(), "", validator);
      consumer.accept(validator.getCreatedElement());
    }

  }

  public static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
    do {
      createInitPy(created);
      created = created.getParent();
    } while(created != null && !created.equals(ancestor));
  }

  private static JBPopup createLightWeightPopup(CreateDirectoryOrPackageHandler validator,
                                                Consumer<PsiFileSystemItem> consumer) {
    NewItemSimplePopupPanel contentPanel = new NewItemSimplePopupPanel();
    JTextField nameField = contentPanel.getTextField();
    JBPopup popup = NewItemPopupUtil.createNewItemPopup(IdeBundle.message("title.new.package"), contentPanel, nameField);
    contentPanel.setApplyAction(event -> {
      String name = nameField.getText();
      if (validator.checkInput(name) && validator.canClose(name)) {
        popup.closeOk(event);
        consumer.accept(validator.getCreatedElement());
      }
      else {
        String errorMessage = validator.getErrorText(name);
        contentPanel.setError(errorMessage);
      }
    });

    return popup;
  }

  private static void createInitPy(PsiDirectory directory) {
    final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(directory.getProject());
    final FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
    if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
      return;
    }

    try {
      FileTemplateUtil.createFromTemplate(template, PyNames.INIT_DOT_PY, fileTemplateManager.getDefaultProperties(), directory);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = isEnabled(e);
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
    if (project == null || ideView == null) {
      return false;
    }
    final PsiDirectory[] directories = ideView.getDirectories();
    if (directories.length == 0) {
      return false;
    }
    return true;
  }
}
