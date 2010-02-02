package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.PathUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
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
    final List<PyFunction> methods = new ArrayList<PyFunction>();
    for (PyMemberInfo member : selectedMemberInfos) {
      final PyElement element = member.getMember();
      if (element instanceof PyFunction) methods.add((PyFunction)element);
      else if (element instanceof PyClass) superClasses.add(element.getName());
      else LOG.error("unmatched member class " + element.getClass());
    }
    final Project project = clazz.getProject();
    final Ref<PyClass> newClassRef = new Ref<PyClass>();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final PyElement[] elements = methods.toArray(new PyElement[methods.size()]);
            final String text = PyClassRefactoringUtil.prepareClassText(clazz, elements, true, true, superBaseName) + "\n";
            final PyClass newClass = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyClass.class, text);
            newClassRef.set(newClass);
            PyClassRefactoringUtil.moveSuperclasses(clazz, superClasses, newClass);
            PyClassRefactoringUtil.addSuperclasses(project, clazz, Collections.singleton(superBaseName));
            PyPsiUtils.removeElements(elements);
            PyClassRefactoringUtil.insertPassIfNeeded(clazz);
            placeNewClass(project, newClass, clazz, targetFile);
          }
        });
      }
    }, RefactoringBundle.message("extract.superclass.command.name", clazz.getName(), superBaseName), null);
    return newClassRef.get();
  }

  private static void placeNewClass(Project project, PyClass newClass, PyClass clazz, String targetFile) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(ApplicationManagerEx.getApplicationEx().isUnitTestMode() ? targetFile : VfsUtil.pathToUrl(targetFile));
    // file is the same as the source
    if (file == clazz.getContainingFile().getVirtualFile()) {
      PyPsiUtils.addBeforeInParent(clazz, newClass, newClass.getNextSibling());
      return;
    }

    PsiFile psiFile = null;
    final PsiDirectory psiDir;
    // file does not exist
    if (file == null) {
      final String filename;
      final String path;
      if (targetFile.endsWith(PythonFileType.INSTANCE.getDefaultExtension())) {
        path = PathUtil.getParentPath(targetFile);
        filename = PathUtil.getFileName(targetFile);
      } else {
        path = targetFile;
        filename = constructFilename(newClass);
      }
      try {
        final VirtualFile dir = VfsUtil.createDirectoryIfMissing(path);
        psiDir = dir != null ? PsiManager.getInstance(project).findDirectory(dir) : null;
        psiFile = psiDir != null ? psiDir.createFile(filename) : null;
      } catch (IOException e) {
        LOG.error(e);
      }
    } else if (file.isDirectory()) {
      // existing directory
      psiDir = PsiManager.getInstance(project).findDirectory(file);
      final String filename = constructFilename(newClass);
      LOG.assertTrue(psiDir != null);
      
      psiFile = psiDir.findFile(filename);
      psiFile = psiFile != null ? psiFile : psiDir.createFile(filename);
    } else {
      // existing file
      psiFile = PsiManager.getInstance(project).findFile(file);
    }

    LOG.assertTrue(psiFile != null);
    insertImport(project, clazz, newClass, psiFile.getVirtualFile());
    PyPsiUtils.addToEnd(psiFile, newClass, newClass.getNextSibling());
  }

  private static void insertImport(Project project, PyClass clazz, PyClass newClass, VirtualFile newFile) {
    final String path = findPackageName(project, newFile);
    final String oldPath = findPackageName(project, clazz.getContainingFile().getVirtualFile());
    LOG.assertTrue(path != null && oldPath != null);

    final String name = newClass.getName();
    final StringBuilder text = new StringBuilder();
    if (!Comparing.strEqual(path, oldPath)) {
      //noinspection ConstantConditions
      text.append("from ").append(path).append(".").append(newClass.getName().toLowerCase()).append(" ");
    }
    text.append("import ").append(name).append("\n");

    final PyStatement imp = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyStatement.class, text.toString());
    PyPsiUtils.addBeforeInParent(clazz, imp, imp.getNextSibling());
  }

  @Nullable
  private static String findPackageName(Project project, VirtualFile newFile) {
    for (VirtualFile file : ProjectRootManager.getInstance(project).getContentRoots()) {
      if (VfsUtil.isAncestor(file, newFile, true)) {
        return VfsUtil.getRelativePath(newFile.getParent(), file, '.');
      }
    }
    return null;
  }

  private static String constructFilename(PyClass newClass) {
    //noinspection ConstantConditions
    return newClass.getName().toLowerCase() + "." + PythonFileType.INSTANCE.getDefaultExtension();
  }
}
