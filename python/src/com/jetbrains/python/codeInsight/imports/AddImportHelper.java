package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Does the actual job of adding an import statement into a file.
 * User: dcheryasov
 * Date: Apr 24, 2009 3:17:59 AM
 */
public class AddImportHelper {
  private static final Logger LOG = Logger.getInstance("#" + AddImportHelper.class.getName());

  private AddImportHelper() {
  }

  public enum ImportPriority {
    BUILTIN, THIRD_PARTY, PROJECT
  }

  private static PsiElement getInsertPosition(final PsiFile file, String nameToImport, ImportPriority priority) {
    PsiElement feeler = file.getFirstChild();
    LOG.assertTrue(feeler != null);
    // skip initial comments and whitespace and try to get just below the last import stmt
    boolean skipped_over_imports = false;
    boolean skipped_over_doc = false;
    PsiElement seeker = feeler;
    do {
      if (feeler instanceof PyImportStatementBase) {
        if (shouldInsertBefore(file, (PyImportStatementBase)feeler, nameToImport, priority)) {
          break;
        }
        seeker = feeler;
        feeler = feeler.getNextSibling();
        skipped_over_imports = true;
      }
      else if (PyUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
        seeker = feeler;
        feeler = feeler.getNextSibling();
      }
      // maybe we arrived at the doc comment stmt; skip over it, too
      else if (!skipped_over_imports && !skipped_over_doc && file instanceof PyFile) {
        PsiElement doc_elt =
          PythonDocStringFinder.find((PyElement)file); // this gives the literal; its parent is the expr seeker may have encountered
        if (doc_elt != null && doc_elt.getParent() == feeler) {
          feeler = feeler.getNextSibling();
          seeker = feeler; // skip over doc even if there's nothing below it
          skipped_over_doc = true;
        }
        else {
          break; // not a doc comment, stop on it
        }
      }
      else {
        break; // some other statement, stop
      }
    }
    while (feeler != null);
    return seeker;
  }

  private static boolean shouldInsertBefore(PsiFile file, PyImportStatementBase relativeTo, String nameToImport, ImportPriority priority) {
    PyQualifiedName relativeToName;
    PsiElement source;
    if (relativeTo instanceof PyFromImportStatement) {
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)relativeTo;
      relativeToName = fromImportStatement.getImportSourceQName();
      source = ResolveImportUtil.resolveFromImportStatementSource(fromImportStatement);
    }
    else {
      final PyImportElement[] importElements = relativeTo.getImportElements();
      if (importElements.length == 0) {
        return false;
      }
      relativeToName = importElements[0].getImportedQName();
      source = ResolveImportUtil.resolveImportElement(importElements[0]);
    }
    if (relativeToName == null) {
      return false;
    }
    final PsiFileSystemItem containingFile;
    if (source instanceof PsiDirectory) {
      containingFile = (PsiDirectory)source;
    }
    else {
      containingFile = source != null ? source.getContainingFile() : null;
    }
    ImportPriority relativeToPriority = source == null ? ImportPriority.BUILTIN : getImportPriority(file, containingFile);
    final int rc = priority.compareTo(relativeToPriority);
    if (rc < 0) {
      return true;
    }
    if (rc == 0) {
      return nameToImport.compareTo(relativeToName.toString()) < 0;
    }
    return false;
  }

  /**
   * Adds an import statement, presumably below all other initial imports in the file.
   *
   * @param file   where to operate
   * @param name   which to import (qualified is OK)
   * @param asName optional name for 'as' clause
   */
  public static void addImportStatement(PsiFile file, String name, @Nullable String asName, ImportPriority priority) {
    String as_clause;
    if (asName == null) {
      as_clause = "";
    }
    else {
      as_clause = " as " + asName;
    }
    List<PyImportElement> existingImports = ((PyFile)file).getImportTargets();
    for (PyImportElement element : existingImports) {
      final PyQualifiedName qName = element.getImportedQName();
      if (qName != null && name.equals(qName.toString())) {
        if ((asName != null && asName.equals(element.getAsName())) || asName == null) {
          return;
        }
      }
    }

    final PyImportStatement importNodeToInsert = PyElementGenerator.getInstance(file.getProject()).createImportStatementFromText(
      "import " + name + as_clause);
    try {
      file.addBefore(importNodeToInsert, getInsertPosition(file, name, priority));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * Adds an "import ... from ..." statement below other top-level imports.
   *
   * @param file   where to operate
   * @param from   name of the module
   * @param name   imported name
   * @param asName optional name for 'as' clause
   */
  public static void addImportFromStatement(PsiFile file, String from, String name, @Nullable String asName, ImportPriority priority) {
    String as_clause;
    if (asName == null) {
      as_clause = "";
    }
    else {
      as_clause = " as " + asName;
    }
    final PyFromImportStatement importNodeToInsert = PyElementGenerator.getInstance(file.getProject()).createFromText(
      LanguageLevel.getDefault(), PyFromImportStatement.class, "from " + from + " import " + name + as_clause);
    try {
      file.addBefore(importNodeToInsert, getInsertPosition(file, from, priority));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static void addImportFrom(PsiFile file, String path, final String name, ImportPriority priority) {
    final List<PyFromImportStatement> existingImports = ((PyFile)file).getFromImports();
    for (PyFromImportStatement existingImport : existingImports) {
      final PyQualifiedName qName = existingImport.getImportSourceQName();
      if (qName != null && qName.toString().equals(path)) {
        for (PyImportElement el : existingImport.getImportElements()) {
          if (name.equals(el.getVisibleName())) {
            return;
          }
        }
        PyImportElement importElement = PyElementGenerator.getInstance(file.getProject()).createImportElement(name);
        existingImport.add(importElement);
        return;
      }
    }
    addImportFromStatement(file, path, name, null, priority);
  }

  public static void addImport(final PsiNamedElement target, final PsiFile file, final PyElement element) {
    final boolean useQualified = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    final PsiFileSystemItem toImport = target instanceof PsiFileSystemItem ? ((PsiFileSystemItem)target).getParent() : target.getContainingFile();
    final ImportPriority priority = getImportPriority(file, toImport);
    final PyQualifiedName qName = ResolveImportUtil.findCanonicalImportPath(target, element);
    if (qName == null) return;
    String path = qName.toString();
    if (target instanceof PsiFileSystemItem) {
      addImportStatement(file, path, null, priority);
    }
    else if (useQualified) {
      addImportStatement(file, path, null, priority);
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
      element.replace(elementGenerator.createExpressionFromText(qName + "." + target.getName()));
    }
    else {
      addImportFrom(file, path, target.getName(), priority);
    }
  }

  public static ImportPriority getImportPriority(PsiElement importLocation, @NotNull PsiFileSystemItem toImport) {
    final VirtualFile vFile = toImport.getVirtualFile();
    if (vFile == null) {
      return ImportPriority.PROJECT;
    }
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(toImport.getProject());
    if (projectRootManager.getFileIndex().isInContent(vFile)) {
      return ImportPriority.PROJECT;
    }
    Module module = ModuleUtil.findModuleForPsiElement(importLocation);
    Sdk pythonSdk = module != null ? PythonSdkType.findPythonSdk(module) : projectRootManager.getProjectSdk();

    return PythonSdkType.isStdLib(vFile, pythonSdk) ? ImportPriority.BUILTIN : ImportPriority.THIRD_PARTY;
  }
}
