// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.application.options.RegistryManager;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateDirectoryOrPackageHandler;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesStatisticsCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class CreatePackageAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreatePackageAction.class);
  private static final @NonNls String NAMESPACE_PACKAGE_TYPE = "Namespace Package";
  private static final @NonNls String ORDINARY_PACKAGE_TYPE = "Package";


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }

    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory == null) return;

    Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) return;

    final CreateFileFromTemplateDialog.Builder builder = createDialogBuilder(project);

    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    final SmartPsiElementPointer<PsiDirectory> directoryPointer = pointerManager.createSmartPsiElementPointer(directory);
    final CreateDirectoryOrPackageHandler newOrdinaryPackageHandler = getNewOrdinaryPackageHandler(project, directory, directoryPointer);
    final CreateDirectoryOrPackageHandler newNamespacePackageHandler = getNewNamespacePackageHandler(project, directory, directoryPointer, module);

    builder.show(PyBundle.message("dialog.title.can.t.create.package"), ORDINARY_PACKAGE_TYPE,
                 new CreateFileFromTemplateDialog.FileCreator<PsiDirectory>() {
                   @Override
                   public PsiDirectory createFile(@NotNull String name, @NotNull String templateName) {
                     if (templateName.equals(ORDINARY_PACKAGE_TYPE)) {
                       createNewPackage(name, newOrdinaryPackageHandler, item -> {
                         if (item != null) {
                           view.selectElement(item);
                         }
                       });
                     }
                     else if (templateName.equals(NAMESPACE_PACKAGE_TYPE)) {
                       createNewPackage(name, newNamespacePackageHandler, item -> {
                         if (item != null) {
                           view.selectElement(item);
                         }
                       });
                       PyNamespacePackagesStatisticsCollector.Companion.logNamespacePackageCreatedByUser();
                     }
                     return directory;
                   }

                   @Override
                   public boolean startInWriteAction() {
                     return false;
                   }

                   @Override
                   @NotNull
                   public String getActionName(@NotNull String name, @NotNull String templateName) {
                     return PyBundle.message("command.name.create.new.package", name);
                   }
                 }, EmptyConsumer.getInstance());
  }

  private static CreateDirectoryOrPackageHandler getNewOrdinaryPackageHandler(@NotNull Project project,
                                                                              @NotNull PsiDirectory directory,
                                                                              @NotNull SmartPsiElementPointer<PsiDirectory> directoryPointer) {
    return new CreateDirectoryOrPackageHandler(project, directory, false, ".") {
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
  }

  private static CreateDirectoryOrPackageHandler getNewNamespacePackageHandler(@NotNull Project project,
                                                                               @NotNull PsiDirectory directory,
                                                                               @NotNull SmartPsiElementPointer<PsiDirectory> directoryPointer,
                                                                               @NotNull Module module) {
    return new CreateDirectoryOrPackageHandler(project, directory, false, ".") {
      @Override
      protected void createDirectories(String subDirName) {
        super.createDirectories(subDirName);

        final PsiDirectory restoredDirectory = directoryPointer.getElement();
        if (restoredDirectory == null) return;
        PsiFileSystemItem element = getCreatedElement();
        PsiFileSystemItem lastElement = element;
        while (element != null && !element.equals(restoredDirectory)) {
          lastElement = element;
          element = element.getParent();
        }
        if (element == null) return;

        VirtualFile topmostCreatedDirectory = lastElement.getVirtualFile();
        if (topmostCreatedDirectory == null) return;
        PyNamespacePackagesService.getInstance(module).toggleMarkingAsNamespacePackage(topmostCreatedDirectory);
      }
    };
  }

  private static void createNewPackage(@NotNull String name,
                                       @NotNull CreateDirectoryOrPackageHandler createHandler,
                                       @NotNull Consumer<PsiFileSystemItem> consumer) {
    if (createHandler.checkInput(name) && createHandler.canClose(name)) {
      consumer.accept(createHandler.getCreatedElement());
    }
    else {
      String errorMessage = createHandler.getErrorText(name);
      Messages.showErrorDialog(errorMessage, PyBundle.message("dialog.title.can.t.create.package"));
    }
  }

  @SuppressWarnings("TestOnlyProblems")
  private static CreateFileFromTemplateDialog.Builder createDialogBuilder(@NotNull Project project) {
    CreateFileFromTemplateDialog.Builder builder = CreateFileFromTemplateDialog.createDialog(project);
    builder
      .setTitle(PyBundle.message("dialog.title.new.python.package"))
      .addKind(PyBundle.message("new.package.list.item.ordinary.package"), PlatformIcons.PACKAGE_ICON, ORDINARY_PACKAGE_TYPE);

    if (PyNamespacePackagesService.isEnabled() && RegistryManager.getInstance().is("python.create.namespace.package.action")) {
      builder.addKind(PyBundle.message("new.package.list.item.namespace.package"), PlatformIcons.PACKAGE_ICON, NAMESPACE_PACKAGE_TYPE);
    }

    return builder;
  }


  public static void createInitPyInHierarchy(@NotNull PsiDirectory created, @NotNull PsiDirectory ancestor) {
    if (created.equals(ancestor)) {
      createInitPy(created);
      return;
    }
    do {
      createInitPy(created);
      created = created.getParent();
    }
    while (created != null && !created.equals(ancestor));
  }

  private static void createInitPy(@NotNull PsiDirectory directory) {
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
