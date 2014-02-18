package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.BadDataException;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterNoPreviewImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassPresenterImpl extends MembersBasedPresenterNoPreviewImpl<PyExtractSuperclassView>
  implements PyExtractSuperclassPresenter {
  private final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());


  @NotNull
  private static final MultiMap<PsiElement, String> EMPTY_MAP = MultiMap.create();

  PyExtractSuperclassPresenterImpl(@NotNull final PyExtractSuperclassView view,
                                   @NotNull final PyClass classUnderRefactoring,
                                   @NotNull final PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage);
  }

  @NotNull
  @Override
  protected MultiMap<PsiElement, String> getConflicts() {
    return EMPTY_MAP; //There are no conflicts for extracting
  }

  @Override
  protected void validateView() throws BadDataException {
    super.validateView();
    final Project project = myClassUnderRefactoring.getProject();
    if (!myNamesValidator.isIdentifier(myView.getSuperClassName(), project)) {
      throw new BadDataException(PyBundle.message("refactoring.extract.super.name.0.must.be.ident", myView.getSuperClassName()));
    }
    boolean rootFound = false;
    try {
      final String targetDir = FileUtil.toSystemIndependentName(new File(myView.getModuleFile()).getCanonicalPath());
      for (final VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
        if (StringUtil.startsWithIgnoreCase(targetDir, file.getPath())) {
          rootFound = true;
          break;
        }
      }
    }
    catch (final IOException ignore) {
    }
    if (!rootFound) {
      throw new BadDataException(PyBundle.message("refactoring.extract.super.target.path.outside.roots"));
    }
  }

  @Override
  public void launch() {
    final String defaultFilePath = FileUtil.toSystemDependentName(myClassUnderRefactoring.getContainingFile().getVirtualFile().getPath());
    final VirtualFile[] roots = ProjectRootManager.getInstance(myClassUnderRefactoring.getProject()).getContentRoots();
    final Collection<PyMemberInfo<PyElement>> pyMemberInfos =
      PyUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring));
    myView.configure(
      new PyExtractSuperclassInitializationInfo(new PyExtractSuperclassInfoModel(myClassUnderRefactoring), pyMemberInfos, defaultFilePath,
                                                roots));
    myView.initAndShow();

  }

  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("extract.superclass.command.name", myView.getSuperClassName(), myClassUnderRefactoring.getName());
  }

  @Override
  protected void refactorNoPreview() {
    PyExtractSuperclassHelper
      .extractSuperclass(myClassUnderRefactoring, myView.getSelectedMemberInfos(), myView.getSuperClassName(), myView.getModuleFile());
  }
}
