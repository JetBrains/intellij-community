package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.AddImportHelper;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

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
            final String text = PyClassRefactoringUtil.prepareClassText(clazz, elements, true, true, superBaseName) + "\n";
            final PyClass newClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class, text);
            newClassRef.set(newClass);
            PyClassRefactoringUtil.moveSuperclasses(clazz, superClasses, newClass);
            PyClassRefactoringUtil.addSuperclasses(project, clazz, null, Collections.singleton(superBaseName));
            if (elements.length > 0) {
              PyPsiUtils.removeElements(elements);
            }
            PyClassRefactoringUtil.insertPassIfNeeded(clazz);
            placeNewClass(project, newClass, clazz, targetFile);
          }
        });
      }
    }, RefactoringBundle.message("extract.superclass.command.name", superBaseName, clazz.getName()), null);
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
    newClass = (PyClass)psiFile.add(newClass);
    insertImport(clazz, newClass, psiFile.getVirtualFile());
  }

  private static void insertImport(PyClass clazz, PyClass newClass, VirtualFile vFile) {
    final PsiFile file = clazz.getContainingFile();
    if (!PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
      final String name = newClass.getQualifiedName();
      AddImportHelper.addImportStatement(file, name, null);
    } else {
      AddImportHelper.addImportFrom(file, ResolveImportUtil.findShortestImportableName(clazz, vFile), newClass.getName());
    }
  }

  private static String constructFilename(PyClass newClass) {
    //noinspection ConstantConditions
    return newClass.getName().toLowerCase() + "." + PythonFileType.INSTANCE.getDefaultExtension();
  }
}
