// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.google.common.base.Predicate;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.jetbrains.python.psi.resolve.PyNamespacePackageUtil.isInNamespacePackage;
import static com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil.placeFile;

/**
 * @author Dennis.Ushakov
 */
public final class PyExtractSuperclassHelper {
  private static final Logger LOG = Logger.getInstance(PyExtractSuperclassHelper.class.getName());
  /**
   * Accepts only those members whose element is PyClass object (new classes)
   */
  private static final Predicate<PyMemberInfo<PyElement>> ALLOW_OBJECT = new PyMembersUtil.ObjectPredicate(true);

  private PyExtractSuperclassHelper() {
  }

  @ApiStatus.Internal
  public static void extractSuperclass(final PyClass clazz,
                                @NotNull Collection<PyMemberInfo<PyElement>> selectedMemberInfos,
                                final String superBaseName,
                                final String targetFile) {
    final Project project = clazz.getProject();

    //We will need to change it probably while param may be read-only
    //noinspection AssignmentToMethodParameter
    selectedMemberInfos = new ArrayList<>(selectedMemberInfos);

    final RefactoringEventData beforeData = new RefactoringEventData();
    beforeData.addElements(JBIterable.from(selectedMemberInfos).transform(
      (Function<PyMemberInfo<PyElement>, PsiElement>)info -> info.getMember()).toList());

    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .refactoringStarted(getRefactoringId(), beforeData);

    // PY-12171
    final PyMemberInfo<PyElement> objectMember = MembersManager.findMember(selectedMemberInfos, ALLOW_OBJECT);
    if (LanguageLevel.forElement(clazz).isPy3K() && !isObjectParentDeclaredExplicitly(clazz)) {
      // Remove object from list if Py3
      if (objectMember != null) {
        selectedMemberInfos.remove(objectMember);
      }
    } else {
      // Always add object if < Py3
      if (objectMember == null) {
        final PyMemberInfo<PyElement> object = MembersManager.findMember(clazz, ALLOW_OBJECT);
        if (object != null) {
          selectedMemberInfos.add(object);
        }
      }
    }

    final String text = "class " + superBaseName + ":\n  pass" + "\n";
    PyClass newClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class, text);

    newClass = placeNewClass(project, newClass, clazz, targetFile);
    MembersManager.moveAllMembers(selectedMemberInfos, clazz, newClass);
    if (! newClass.getContainingFile().equals(clazz.getContainingFile())) {
      PyClassRefactoringUtil.optimizeImports(clazz.getContainingFile()); // To remove unneeded imports only if user used different file
    }
    PyPsiRefactoringUtil.addSuperclasses(project, clazz, null, newClass);

    final RefactoringEventData afterData = new RefactoringEventData();
    afterData.addElement(newClass);
    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .refactoringDone(getRefactoringId(), afterData);
  }

  /**
   * If class explicitly extends object we shall move it even in Py3K
   */
  private static boolean isObjectParentDeclaredExplicitly(final @NotNull PyClass clazz) {
    return ContainerUtil.exists(clazz.getSuperClassExpressions(), o -> PyNames.OBJECT.equals(o.getName()));
  }

  private static boolean isRefactoredClassInBackedFile(@Nullable VirtualFile targetFile, @NotNull PyClass pyClass) {
    VirtualFile file = pyClass.getContainingFile().getVirtualFile();
    return file instanceof BackedVirtualFile &&
           Comparing.equal(((BackedVirtualFile)file).getOriginFile(), targetFile);
  }
  
  private static PyClass placeNewClass(final @NotNull Project project, @NotNull PyClass newClass, final @NotNull PyClass clazz, final @NotNull String targetFile) {
    VirtualFile file = VirtualFileManager.getInstance()
      .findFileByUrl(ApplicationManager.getApplication().isUnitTestMode() ? targetFile : VfsUtilCore.pathToUrl(targetFile));
    // file is the same as the source
    if (Comparing.equal(file, clazz.getContainingFile().getVirtualFile()) || isRefactoredClassInBackedFile(file, clazz)) {
      return (PyClass)clazz.getParent().addBefore(newClass, clazz);
    }
    boolean isNamespace = isInNamespacePackage(clazz);
    PsiFile psiFile = null;
    try {
      if (file == null) {
        // file does not exist
        final String filename;
        final String path;
        if (targetFile.endsWith(PythonFileType.INSTANCE.getDefaultExtension())) {
          path = PathUtil.getParentPath(targetFile);
          filename = PathUtil.getFileName(targetFile);
        }
        else {
          path = targetFile;
          filename = PyNames.INIT_DOT_PY; // user requested putting the class into this package directly
        }
        psiFile = placeFile(project, path, filename, isNamespace);
      }
      else if (file.isDirectory()) { // existing directory
        psiFile = placeFile(project, file.getPath(), PyNames.INIT_DOT_PY, isNamespace);
      }
      else { // existing file
        psiFile = PsiManager.getInstance(project).findFile(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    LOG.assertTrue(psiFile != null);
    if (psiFile.getLastChild() != null) {
      // TODO: make the number of newlines depend on style setting
      psiFile.add(PyElementGenerator.getInstance(project).createFromText(LanguageLevel.PYTHON27, PsiWhiteSpace.class, "\n\n"));
    }
    newClass = (PyClass)psiFile.add(newClass);
    PyPsiRefactoringUtil.insertImport(clazz, Collections.singleton(newClass));
    return newClass;
  }





  public static String getRefactoringId() {
    return "refactoring.python.extract.superclass";
  }

}
