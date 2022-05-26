/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersUtil;
import com.jetbrains.python.refactoring.classes.membersManager.vp.BadDataException;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterNoPreviewImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassPresenterImpl extends MembersBasedPresenterNoPreviewImpl<PyExtractSuperclassView,
  MemberInfoModel<PyElement, PyMemberInfo<PyElement>>>
  implements PyExtractSuperclassPresenter {
  private final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());

  PyExtractSuperclassPresenterImpl(@NotNull final PyExtractSuperclassView view,
                                   @NotNull final PyClass classUnderRefactoring,
                                   @NotNull final PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage, new PyExtractSuperclassInfoModel(classUnderRefactoring));
  }

  @Override
  protected void validateView() throws BadDataException {
    super.validateView();
    final Project project = myClassUnderRefactoring.getProject();
    if (!myNamesValidator.isIdentifier(myView.getSuperClassName(), project)) {
      throw new BadDataException(PyBundle.message("refactoring.extract.super.name.0.must.be.ident", myView.getSuperClassName()));
    }
    boolean rootFound = false;
    final File moduleFile = new File(myView.getModuleFile());
    try {
      final String targetDir = FileUtil.toSystemIndependentName(moduleFile.getCanonicalPath());
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

    // TODO: Cover with test. It can't be done for now, because testFixture reports root path incorrectly
    // PY-12173
    myView.getModuleFile();
    final VirtualFile moduleVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(moduleFile);
    if (moduleVirtualFile != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(moduleVirtualFile);
      if (psiFile instanceof PyFile) {
        if (((PyFile)psiFile).findTopLevelClass(myView.getSuperClassName()) != null) {
          throw new BadDataException(PyBundle.message("refactoring.extract.super.target.class.already.exists", myView.getSuperClassName()));
        }
      }
    }
  }

  @Override
  public void launch() {
    final String defaultFilePath = FileUtil.toSystemDependentName(myClassUnderRefactoring.getContainingFile().getVirtualFile().getPath());
    final VirtualFile[] roots = ProjectRootManager.getInstance(myClassUnderRefactoring.getProject()).getContentRoots();
    final Collection<PyMemberInfo<PyElement>> pyMemberInfos =
      PyMembersUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring));
    myView.configure(
      new PyExtractSuperclassInitializationInfo(myModel, pyMemberInfos, defaultFilePath,
                                                roots)
    );
    myView.initAndShow();
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("extract.superclass.command.name", myView.getSuperClassName(), myClassUnderRefactoring.getName());
  }

  @Override
  protected void refactorNoPreview() {
    var infos = myView.getSelectedMemberInfos();
    disableItemsCantBeAbstract(infos);
    PyExtractSuperclassHelper
      .extractSuperclass(myClassUnderRefactoring, infos, myView.getSuperClassName(), myView.getModuleFile());
  }

  @NotNull
  @Override
  protected Iterable<? extends PyClass> getDestClassesToCheckConflicts() {
    return Collections.emptyList(); // No conflict can take place in newly created classes
  }

  /**
   * Methods may still be marked abstract because user marked them before they were disabled
   */
  private void disableItemsCantBeAbstract(@NotNull Collection<PyMemberInfo<PyElement>> infos) {
    for (var info : infos) {
      if (info.isToAbstract() && !myModel.isAbstractEnabled(info)) {
        info.setToAbstract(false);
      }
    }
  }
}
