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

import com.google.common.base.Predicate;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.JBIterable;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public final class PyExtractSuperclassHelper {
  private static final Logger LOG = Logger.getInstance(PyExtractSuperclassHelper.class.getName());
  /**
   * Accepts only those members whose element is PyClass object (new classes)
   */
  private static final Predicate<PyMemberInfo<PyElement>> ALLOW_OBJECT = new PyUtil.ObjectPredicate(true);

  private PyExtractSuperclassHelper() {
  }

  static void extractSuperclass(final PyClass clazz,
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
    PyClassRefactoringUtil.addSuperclasses(project, clazz, null, newClass);

    final RefactoringEventData afterData = new RefactoringEventData();
    afterData.addElement(newClass);
    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .refactoringDone(getRefactoringId(), afterData);
  }

  /**
   * If class explicitly extends object we shall move it even in Py3K
   */
  private static boolean isObjectParentDeclaredExplicitly(@NotNull final PyClass clazz) {
    return Arrays.stream(clazz.getSuperClassExpressions()).filter(o -> PyNames.OBJECT.equals(o.getName())).findFirst().isPresent();
  }

  private static PyClass placeNewClass(final Project project, PyClass newClass, @NotNull final PyClass clazz, final String targetFile) {
    VirtualFile file = VirtualFileManager.getInstance()
      .findFileByUrl(ApplicationManagerEx.getApplicationEx().isUnitTestMode() ? targetFile : VfsUtilCore.pathToUrl(targetFile));
    // file is the same as the source
    if (Comparing.equal(file, clazz.getContainingFile().getVirtualFile())) {
      return (PyClass)clazz.getParent().addBefore(newClass, clazz);
    }

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
        psiFile = placeFile(project, path, filename);
      }
      else if (file.isDirectory()) { // existing directory
        psiFile = placeFile(project, file.getPath(), PyNames.INIT_DOT_PY);
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
      psiFile.add(PyElementGenerator.getInstance(project).createFromText(LanguageLevel.PYTHON24, PsiWhiteSpace.class, "\n\n"));
    }
    newClass = (PyClass)psiFile.add(newClass);
    PyClassRefactoringUtil.insertImport(clazz, Collections.singleton((PsiNamedElement)newClass));
    return newClass;
  }

  /**
   * Places a file at the end of given path, creating intermediate dirs and inits.
   *
   * @param project
   * @param path
   * @param filename
   * @return the placed file
   * @throws IOException
   */
  public static PsiFile placeFile(Project project, String path, String filename) throws IOException {
    return placeFile(project, path, filename, null);
  }

  //TODO: Mover to the other class? That is not good to dependent PyUtils on this class
  public static PsiFile placeFile(Project project, String path, String filename, @Nullable String content) throws IOException {
    PsiDirectory psiDir = createDirectories(project, path);
    LOG.assertTrue(psiDir != null);
    PsiFile psiFile = psiDir.findFile(filename);
    if (psiFile == null) {
      psiFile = psiDir.createFile(filename);
      if (content != null) {
        final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        final Document document = manager.getDocument(psiFile);
        if (document != null) {
          document.setText(content);
          manager.commitDocument(document);
        }
      }
    }
    return psiFile;
  }

  /**
   * Create all intermediate dirs with inits from one of roots up to target dir.
   *
   * @param project
   * @param target  a full path to target dir
   * @return deepest child directory, or null if target is not in roots or process fails at some point.
   */
  @Nullable
  private static PsiDirectory createDirectories(Project project, String target) throws IOException {
    String the_rest = null;
    VirtualFile the_root = null;
    PsiDirectory ret = null;

    // NOTE: we don't canonicalize target; must be ok in reasonable cases, and is far easier in unit test mode
    target = FileUtil.toSystemIndependentName(target);
    for (VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
      final String root_path = file.getPath();
      if (target.startsWith(root_path)) {
        the_rest = target.substring(root_path.length());
        the_root = file;
        break;
      }
    }
    if (the_root == null) {
      throw new IOException("Can't find '" + target + "' among roots");
    }
    if (the_rest != null) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      final PsiManager psi_mgr = PsiManager.getInstance(project);
      String[] dirs = the_rest.split("/");
      int i = 0;
      if ("".equals(dirs[0])) i = 1;
      while (i < dirs.length) {
        VirtualFile subdir = the_root.findChild(dirs[i]);
        if (subdir != null) {
          if (!subdir.isDirectory()) {
            throw new IOException("Expected dir, but got non-dir: " + subdir.getPath());
          }
        }
        else {
          subdir = the_root.createChildDirectory(lfs, dirs[i]);
        }
        VirtualFile init_vfile = subdir.findChild(PyNames.INIT_DOT_PY);
        if (init_vfile == null) init_vfile = subdir.createChildData(lfs, PyNames.INIT_DOT_PY);
        /*
        // here we could add an __all__ clause to the __init__.py.
        // * there's no point to do so; we import the class directly;
        // * we can't do this consistently since __init__.py may already exist and be nontrivial.
        if (i == dirs.length - 1) {
          PsiFile init_file = psi_mgr.findFile(init_vfile);
          LOG.assertTrue(init_file != null);
          final PyElementGenerator gen = PyElementGenerator.getInstance(project);
          final PyStatement statement = gen.createFromText(LanguageLevel.getDefault(), PyStatement.class, PyNames.ALL + " = [\"" + lastName + "\"]");
          init_file.add(statement);
        }
        */
        the_root = subdir;
        i += 1;
      }
      ret = psi_mgr.findDirectory(the_root);
    }
    return ret;
  }

  public static String getRefactoringId() {
    return "refactoring.python.extract.superclass";
  }
}
