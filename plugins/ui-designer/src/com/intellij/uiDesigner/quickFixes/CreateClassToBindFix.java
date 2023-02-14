// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class CreateClassToBindFix extends QuickFix{
  private static final Logger LOG = Logger.getInstance(CreateClassToBindFix.class);

  private final String myClassName;

  public CreateClassToBindFix(final GuiEditor editor, @NotNull final String className) {
    super(editor, UIDesignerBundle.message("action.create.class", className), null);
    myClassName = className;
  }

  @Override
  public void run() {
    final Project project = myEditor.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(myEditor.getFile());
    if(sourceRoot == null){
      Messages.showErrorDialog(
        myEditor,
        UIDesignerBundle.message("error.cannot.create.class.not.in.source.root"),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    ApplicationManager.getApplication().runWriteAction(
      () -> CommandProcessor.getInstance().executeCommand(
        project,
        () -> {
          // 1. Create all necessary packages
          final int indexOfLastDot = myClassName.lastIndexOf('.');
          final String packageName = myClassName.substring(0, indexOfLastDot != -1 ? indexOfLastDot : 0);
          final PsiDirectory psiDirectory;
          if(packageName.length() > 0){
            final PackageWrapper packageWrapper = new PackageWrapper(PsiManager.getInstance(project), packageName);
            try {
              psiDirectory = CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, sourceRoot);
            }
            catch (final IncorrectOperationException e) {
              ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(
                myEditor,
                UIDesignerBundle.message("error.cannot.create.package", packageName, e.getMessage()),
                CommonBundle.getErrorTitle()
              ));
              return;
            }
          }
          else{
            psiDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
            LOG.assertTrue(psiDirectory != null);
          }

          // 2. Create class in the package
          try {
            final String name = myClassName.substring(indexOfLastDot != -1 ? indexOfLastDot + 1 : 0);
            final PsiClass aClass = JavaDirectoryService.getInstance().createClass(psiDirectory, name);
            createBoundFields(aClass);
          }
          catch (final IncorrectOperationException e) {
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(
              myEditor,
              UIDesignerBundle.message("error.cannot.create.class", packageName, e.getMessage()),
              CommonBundle.getErrorTitle()
            ));
          }
        },
        getName(),
        null
      )
    );
  }

  private void createBoundFields(final PsiClass formClass) throws IncorrectOperationException {
    final Module module = myEditor.getRootContainer().getModule();
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(myEditor.getProject());

    final Ref<IncorrectOperationException> exception = new Ref<>();
    FormEditingUtil.iterate(myEditor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      @Override
      public boolean visit(final IComponent component) {
        if (component.getBinding() != null) {
          final PsiClass fieldClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
          if (fieldClass != null) {
            PsiType fieldType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(fieldClass);
            try {
              PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createField(component.getBinding(), fieldType);
              formClass.add(field);
            }
            catch (IncorrectOperationException e) {
              exception.set(e);
              return false;
            }
          }
        }
        return true;
      }
    });

    if (!exception.isNull()) {
      throw exception.get();
    }
  }
}
