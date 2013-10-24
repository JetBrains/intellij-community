/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.imports;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
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

  @Nullable
  public static PsiElement getFileInsertPosition(final PsiFile file) {
    return getInsertPosition(file, null, null);
  }

  @Nullable
  private static PsiElement getInsertPosition(final PsiFile file, @Nullable String nameToImport, @Nullable ImportPriority priority) {
    PsiElement feeler = file.getFirstChild();
    if (feeler == null) return null;
    // skip initial comments and whitespace and try to get just below the last import stmt
    boolean skippedOverImports = false;
    boolean skippedOverDoc = false;
    PsiElement seeker = feeler;
    final boolean isInjected = InjectedLanguageManager.getInstance(feeler.getProject()).isInjectedFragment(feeler.getContainingFile());
    do {
      if (feeler instanceof PyImportStatementBase && !isInjected) {
        if (nameToImport != null && priority != null && shouldInsertBefore(file, (PyImportStatementBase)feeler, nameToImport, priority)) {
          break;
        }
        seeker = feeler;
        feeler = feeler.getNextSibling();
        skippedOverImports = true;
      }
      else if (PyUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
        seeker = feeler;
        feeler = feeler.getNextSibling();
      }
      // maybe we arrived at the doc comment stmt; skip over it, too
      else if (!skippedOverImports && !skippedOverDoc && file instanceof PyFile) {
        PsiElement doc_elt =
          DocStringUtil.findDocStringExpression((PyElement)file); // this gives the literal; its parent is the expr seeker may have encountered
        if (doc_elt != null && doc_elt.getParent() == feeler) {
          feeler = feeler.getNextSibling();
          seeker = feeler; // skip over doc even if there's nothing below it
          skippedOverDoc = true;
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
    QualifiedName relativeToName;
    PsiElement source;
    if (relativeTo instanceof PyFromImportStatement) {
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)relativeTo;
      if (fromImportStatement.isFromFuture()) {
        return false;
      }
      relativeToName = fromImportStatement.getImportSourceQName();
      source = fromImportStatement.resolveImportSource();
    }
    else {
      final PyImportElement[] importElements = relativeTo.getImportElements();
      if (importElements.length == 0) {
        return false;
      }
      relativeToName = importElements[0].getImportedQName();
      source = importElements[0].resolve();
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
    ImportPriority relativeToPriority = source == null || containingFile == null
                                        ? ImportPriority.BUILTIN
                                        : getImportPriority(file, containingFile);
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
  public static boolean addImportStatement(PsiFile file, String name, @Nullable String asName, ImportPriority priority) {
    String as_clause;
    if (asName == null) {
      as_clause = "";
    }
    else {
      as_clause = " as " + asName;
    }
    if (!(file instanceof PyFile)) {
      return false;
    }
    List<PyImportElement> existingImports = ((PyFile)file).getImportTargets();
    for (PyImportElement element : existingImports) {
      final QualifiedName qName = element.getImportedQName();
      if (qName != null && name.equals(qName.toString())) {
        if ((asName != null && asName.equals(element.getAsName())) || asName == null) {
          return false;
        }
      }
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(file);
    final PyImportStatement importNodeToInsert = generator.createImportStatementFromText(languageLevel, "import " + name + as_clause);
    try {
      file.addBefore(importNodeToInsert, getInsertPosition(file, name, priority));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
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
    String asClause = asName == null ? "" : " as " + asName;

    final PyFromImportStatement importNodeToInsert = PyElementGenerator.getInstance(file.getProject()).createFromText(
      LanguageLevel.forElement(file), PyFromImportStatement.class, "from " + from + " import " + name + asClause);
    try {
      if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
        final PsiElement element = file.addBefore(importNodeToInsert, getInsertPosition(file, from, priority));
        PsiElement whitespace = element.getNextSibling();
        if (!(whitespace instanceof PsiWhiteSpace))
          whitespace = PsiParserFacade.SERVICE.getInstance(file.getProject()).createWhiteSpaceFromText("  >>> ");
        file.addBefore(whitespace, element);
      }
      else {
        file.addBefore(importNodeToInsert, getInsertPosition(file, from, priority));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static boolean addImportFrom(PsiFile file, @Nullable PsiElement target, String path, final String name,
                                      @Nullable String asName, ImportPriority priority) {
    final List<PyFromImportStatement> existingImports = ((PyFile)file).getFromImports();
    for (PyFromImportStatement existingImport : existingImports) {
      if (target != null && existingImport.getTextRange().getStartOffset() > target.getTextRange().getStartOffset()) {
        continue;
      }
      if (existingImport.isStarImport()) {
        continue;
      }
      final QualifiedName qName = existingImport.getImportSourceQName();
      if (qName != null && qName.toString().equals(path)) {
        for (PyImportElement el : existingImport.getImportElements()) {
          if (name.equals(el.getVisibleName())) {
            return false;
          }
        }
        final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        PyImportElement importElement = generator.createImportElement(LanguageLevel.forElement(file), name);
        existingImport.add(importElement);
        return true;
      }
    }
    addImportFromStatement(file, path, name, asName, priority);
    return true;
  }

  public static void addImport(final PsiNamedElement target, final PsiFile file, final PyElement element) {
    final boolean useQualified = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    final PsiFileSystemItem toImport = target instanceof PsiFileSystemItem ? ((PsiFileSystemItem)target).getParent() : target.getContainingFile();
    final ImportPriority priority = getImportPriority(file, toImport);
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(target, element);
    if (qName == null) return;
    String path = qName.toString();
    if (target instanceof PsiFileSystemItem && qName.getComponentCount() == 1) {
      addImportStatement(file, path, null, priority);
    }
    else {
      final QualifiedName toImportQName = QualifiedNameFinder.findCanonicalImportPath(toImport, element);
      if (useQualified) {
        addImportStatement(file, path, null, priority);
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
        final String targetName = PyUtil.getElementNameWithoutExtension(target);
        element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(target), toImportQName + "." + targetName));
      }
      else {
        addImportFrom(file, null, toImportQName.toString(), target.getName(), null, priority);
      }
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
    Module module = ModuleUtilCore.findModuleForPsiElement(importLocation);
    Sdk pythonSdk = module != null ? PythonSdkType.findPythonSdk(module) : projectRootManager.getProjectSdk();

    return PythonSdkType.isStdLib(vFile, pythonSdk) ? ImportPriority.BUILTIN : ImportPriority.THIRD_PARTY;
  }
}
