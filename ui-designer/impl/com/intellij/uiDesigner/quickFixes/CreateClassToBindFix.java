package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.util.IncorrectOperationException;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateClassToBindFix extends QuickFix{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.quickFixes.CreateClassToBindFix");

  private final String myClassName;

  public CreateClassToBindFix(final GuiEditor editor, @NotNull final String className) {
    super(editor, UIDesignerBundle.message("action.create.class", className), null);
    myClassName = className;
  }

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
      new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(
            project,
            new Runnable() {
              public void run() {
                // 1. Create all necessary packages
                final int indexOfLastDot = myClassName.lastIndexOf('.');
                final String packageName = myClassName.substring(0, indexOfLastDot != -1 ? indexOfLastDot : 0);
                final PsiDirectory psiDirectory;
                if(packageName.length() > 0){
                  final PackageWrapper packageWrapper = new PackageWrapper(PsiManager.getInstance(project), packageName);
                  try {
                    psiDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, sourceRoot);
                    LOG.assertTrue(psiDirectory != null);
                  }
                  catch (final IncorrectOperationException e) {
                    ApplicationManager.getApplication().invokeLater(new Runnable(){
                                        public void run() {
                                          Messages.showErrorDialog(
                                            myEditor,
                                            UIDesignerBundle.message("error.cannot.create.package", packageName, e.getMessage()),
                                            CommonBundle.getErrorTitle()
                                          );
                                        }
                                      });
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
                  final PsiClass aClass = psiDirectory.createClass(name);
                  createBoundFields(aClass);
                }
                catch (final IncorrectOperationException e) {
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                                    public void run() {
                                      Messages.showErrorDialog(
                                        myEditor,
                                        UIDesignerBundle.message("error.cannot.create.class", packageName, e.getMessage()),
                                        CommonBundle.getErrorTitle()
                                      );
                                    }
                                  });
                }
              }
            },
            getName(),
            null
          );
        }
      }
    );
  }

  private void createBoundFields(final PsiClass formClass) throws IncorrectOperationException {
    final Module module = myEditor.getRootContainer().getModule();
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(myEditor.getProject());

    final Ref<IncorrectOperationException> exception = new Ref<IncorrectOperationException>();
    FormEditingUtil.iterate(myEditor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        if (component.getBinding() != null) {
          final PsiClass fieldClass = psiManager.findClass(component.getComponentClassName(), scope);
          if (fieldClass != null) {
            PsiType fieldType = psiManager.getElementFactory().createType(fieldClass);
            try {
              PsiField field = psiManager.getElementFactory().createField(component.getBinding(), fieldType);
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
