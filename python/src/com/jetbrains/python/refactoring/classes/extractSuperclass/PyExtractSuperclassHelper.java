package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.PathUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyExtractSuperclassHelper {
  private static final Logger LOG = Logger.getInstance(PyExtractSuperclassHelper.class.getName());

  private PyExtractSuperclassHelper() {}

  public static PsiElement extractSuperclass(final PyClass clazz,
                                             final Collection<PyMemberInfo> selectedMemberInfos,
                                             final String superBaseName,
                                             final String targetFile) {
    final Set<String> superClasses = new HashSet<String>();
    final Set<PsiNamedElement> extractedClasses = new HashSet<PsiNamedElement>();
    final List<PyFunction> methods = new ArrayList<PyFunction>();
    for (PyMemberInfo member : selectedMemberInfos) {
      final PyElement element = member.getMember();
      if (element instanceof PyFunction) methods.add((PyFunction)element);
      else if (element instanceof PyClass) {
        extractedClasses.add((PyClass)element);
        superClasses.add(element.getName());
      }
      else LOG.error("unmatched member class " + element.getClass());
    }

    // 'object' superclass is always pulled up, even if not selected explicitly
    for (PyExpression expr : clazz.getSuperClassExpressions()) {
      if (PyNames.OBJECT.equals(expr.getText()) && !superClasses.contains(PyNames.OBJECT)) {
        superClasses.add(PyNames.OBJECT);
      }
    }

    final Project project = clazz.getProject();
    final Ref<PyClass> newClassRef = new Ref<PyClass>();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final PyElement[] elements = methods.toArray(new PyElement[methods.size()]);
            final String text = "class " + superBaseName + ":\n  pass" + "\n";
            PyClass newClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class, text);
            newClass = placeNewClass(project, newClass, clazz, targetFile);
            newClassRef.set(newClass);
            PyClassRefactoringUtil.moveMethods(methods, newClass);
            PyClassRefactoringUtil.moveSuperclasses(clazz, superClasses, newClass);
            PyClassRefactoringUtil.addSuperclasses(project, clazz, null, Collections.singleton(superBaseName));
            PyClassRefactoringUtil.insertImport(newClass, extractedClasses);
            if (elements.length > 0) {
              PyPsiUtils.removeElements(elements);
            }
            PyClassRefactoringUtil.insertPassIfNeeded(clazz);
          }
        });
      }
    }, RefactoringBundle.message("extract.superclass.command.name", superBaseName, clazz.getName()), null);
    return newClassRef.get();
  }

  private static PyClass placeNewClass(Project project, PyClass newClass, @NotNull PyClass clazz, String targetFile) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(ApplicationManagerEx.getApplicationEx().isUnitTestMode() ? targetFile : VfsUtil.pathToUrl(targetFile));
    // file is the same as the source
    if (file == clazz.getContainingFile().getVirtualFile()) {
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
    } catch (IOException e) {
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
   * @param project
   * @param path
   * @param filename
   * @return the placed file
   * @throws IOException
   */
  public static PsiFile placeFile(Project project, String path, String filename) throws IOException {
    PsiDirectory psiDir = createDirectories(project, path);
    LOG.assertTrue(psiDir != null);
    PsiFile psiFile = psiDir.findFile(filename);
    psiFile = psiFile != null ? psiFile : psiDir.createFile(filename);
    return psiFile;
  }

  /**
   * Create all intermediate dirs with inits from one of roots up to target dir.
   * @param project
   * @param target a full path to target dir
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
      throw new IOException("Can't find '"+ target +"' among roots");
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
        else subdir = the_root.createChildDirectory(lfs, dirs[i]);
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

}
