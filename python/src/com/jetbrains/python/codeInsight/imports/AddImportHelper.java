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
package com.jetbrains.python.codeInsight.imports;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Does the actual job of adding an import statement into a file.
 * User: dcheryasov
 * Date: Apr 24, 2009 3:17:59 AM
 */
public class AddImportHelper {
  private static final Logger LOG = Logger.getInstance("#" + AddImportHelper.class.getName());

  private AddImportHelper() {
  }

  public static void addLocalImportStatement(@NotNull PsiElement element, @NotNull String name) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(element);

    final PsiElement anchor = getLocalInsertPosition(element);
    final PsiElement parentElement = sure(anchor).getParent();
    if (parentElement != null) {
      parentElement.addBefore(generator.createImportStatement(languageLevel, name, null), anchor);
    }
  }

  public static void addLocalFromImportStatement(@NotNull PsiElement element, @NotNull String qualifier, @NotNull String name) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(element);

    final PsiElement anchor = getLocalInsertPosition(element);
    final PsiElement parentElement = sure(anchor).getParent();
    if (parentElement != null) {
      parentElement.addBefore(generator.createFromImportStatement(languageLevel, qualifier, name, null), anchor);
    }

  }

  @Nullable
  public static PsiElement getLocalInsertPosition(@NotNull PsiElement anchor) {
    return PsiTreeUtil.getParentOfType(anchor, PyStatement.class, false);
  }

  public enum ImportPriority {
    BUILTIN, THIRD_PARTY, PROJECT
  }

  @Nullable
  public static PsiElement getFileInsertPosition(final PsiFile file) {
    return getInsertPosition(file, null, null);
  }

  @Nullable
  private static PsiElement getInsertPosition(final PsiElement insertParent, @Nullable String nameToImport,
                                              @Nullable ImportPriority priority) {
    PsiElement feeler = insertParent.getFirstChild();
    if (feeler == null) return null;
    // skip initial comments and whitespace and try to get just below the last import stmt
    boolean skippedOverImports = false;
    boolean skippedOverDoc = false;
    PsiElement seeker = feeler;
    final boolean isInjected = InjectedLanguageManager.getInstance(feeler.getProject()).isInjectedFragment(feeler.getContainingFile());
    do {
      if (feeler instanceof PyImportStatementBase && !isInjected) {
        if (nameToImport != null && priority != null && shouldInsertBefore(insertParent, (PyImportStatementBase)feeler, nameToImport, priority)) {
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
      else if (!skippedOverImports && !skippedOverDoc && insertParent instanceof PyFile) {
        PsiElement doc_elt =
          DocStringUtil.findDocStringExpression((PyElement)insertParent); // this gives the literal; its parent is the expr seeker may have encountered
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

  private static boolean shouldInsertBefore(PsiElement element, PyImportStatementBase relativeTo, String nameToImport,
                                            ImportPriority priority) {
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
                                        : getImportPriority(element, containingFile);
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
   * Adds an import statement, if it doesn't exist yet, presumably below all other initial imports in the file.
   *
   * @param file   where to operate
   * @param name   which to import (qualified is OK)
   * @param asName optional name for 'as' clause
   * @param anchor place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *               e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *               will be inserted right after it.
   * @return whether import statement was actually added
   */
  public static boolean addImportStatement(@NotNull PsiFile file,
                                           @NotNull String name,
                                           @Nullable String asName,
                                           @Nullable ImportPriority priority,
                                           @Nullable PsiElement anchor) {
    if (!(file instanceof PyFile)) {
      return false;
    }
    final List<PyImportElement> existingImports = ((PyFile)file).getImportTargets();
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
    final PyImportStatement importNodeToInsert = generator.createImportStatement(languageLevel, name, asName);
    final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
    final PsiElement insertParent = importStatement != null && importStatement.getContainingFile() == file ?
                                    importStatement.getParent() : file;
    try {
      if (anchor instanceof PyImportStatementBase) {
        insertParent.addAfter(importNodeToInsert, anchor);
      }
      else {
        insertParent.addBefore(importNodeToInsert, getInsertPosition(insertParent, name, priority));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
  }

  /**
   * Adds a new {@link PyFromImportStatement} statement below other top-level imports or as specified by anchor.
   *
   * @param file   where to operate
   * @param from   import source (reference after {@code from} keyword)
   * @param name   imported name (identifier after {@code import} keyword)
   * @param asName optional alias (identifier after {@code as} keyword)
   * @param anchor place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *               e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *               will be inserted right after it.
   * @see #addOrUpdateFromImportStatement
   */
  public static void addFromImportStatement(@NotNull PsiFile file,
                                            @NotNull String from,
                                            @NotNull String name,
                                            @Nullable String asName,
                                            @Nullable ImportPriority priority,
                                            @Nullable PsiElement anchor) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(file);
    final PyFromImportStatement nodeToInsert = generator.createFromImportStatement(languageLevel, from, name, asName);
    try {
      final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
      final PsiElement insertParent;
      if (importStatement != null && importStatement.getContainingFile() == file) {
        insertParent = importStatement.getParent();
      }
      else {
        insertParent = file;
      }
      if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
        final PsiElement element = insertParent.addBefore(nodeToInsert, getInsertPosition(insertParent, from, priority));
        PsiElement whitespace = element.getNextSibling();
        if (!(whitespace instanceof PsiWhiteSpace)) {
          whitespace = PsiParserFacade.SERVICE.getInstance(file.getProject()).createWhiteSpaceFromText("  >>> ");
        }
        insertParent.addBefore(whitespace, element);
      }
      else {
        if (anchor instanceof PyImportStatementBase) {
          insertParent.addAfter(nodeToInsert, anchor);
        }
        else {
          insertParent.addBefore(nodeToInsert, getInsertPosition(insertParent, from, priority));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * Adds new {@link PyFromImportStatement} in file or append {@link PyImportElement} to
   * existing from import statement.
   *
   * @param file     module where import will be added
   * @param from     import source (reference after {@code from} keyword)
   * @param name     imported name (identifier after {@code import} keyword)
   * @param asName   optional alias (identifier after {@code as} keyword)
   * @param priority optional import priority used to sort imports
   * @param anchor   place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *                 e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *                 will be inserted right after it.
   * @return whether import was actually added
   * @see #addFromImportStatement
   */
  public static boolean addOrUpdateFromImportStatement(@NotNull PsiFile file,
                                                       @NotNull String from,
                                                       @NotNull String name,
                                                       @Nullable String asName,
                                                       @Nullable ImportPriority priority,
                                                       @Nullable PsiElement anchor) {
    final List<PyFromImportStatement> existingImports = ((PyFile)file).getFromImports();
    for (PyFromImportStatement existingImport : existingImports) {
      if (existingImport.isStarImport()) {
        continue;
      }
      final QualifiedName qName = existingImport.getImportSourceQName();
      if (qName != null && qName.toString().equals(from) && existingImport.getRelativeLevel() == 0) {
        for (PyImportElement el : existingImport.getImportElements()) {
          final QualifiedName importedQName = el.getImportedQName();
          if (importedQName != null && StringUtil.equals(name, importedQName.toString()) && StringUtil.equals(asName, el.getAsName())) {
            return false;
          }
        }
        final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        final PyImportElement importElement = generator.createImportElement(LanguageLevel.forElement(file), name);
        existingImport.add(importElement);
        return false;
      }
    }
    addFromImportStatement(file, from, name, asName, priority, anchor);
    return true;
  }

  /**
   * Adds either {@link PyFromImportStatement} or {@link PyImportStatement}
   * to specified target depending on user preferences and whether it's possible to import element via "from" form of import
   * (e.g. consider top level module).
   *
   * @param target  element import is pointing to
   * @param file    file where import will be inserted
   * @param element used to determine where to insert import
   * @see PyCodeInsightSettings#PREFER_FROM_IMPORT
   * @see #addImportStatement
   * @see #addOrUpdateFromImportStatement
   */
  public static void addImport(final PsiNamedElement target, final PsiFile file, final PyElement element) {
    final boolean useQualified = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    final PsiFileSystemItem toImport =
      target instanceof PsiFileSystemItem ? ((PsiFileSystemItem)target).getParent() : target.getContainingFile();
    if (toImport == null) return;
    final ImportPriority priority = getImportPriority(file, toImport);
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(target, element);
    if (qName == null) return;
    String path = qName.toString();
    if (target instanceof PsiFileSystemItem && qName.getComponentCount() == 1) {
      addImportStatement(file, path, null, priority, element);
    }
    else {
      final QualifiedName toImportQName = QualifiedNameFinder.findCanonicalImportPath(toImport, element);
      if (toImportQName == null) return;
      if (useQualified) {
        addImportStatement(file, path, null, priority, element);
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
        final String targetName = PyUtil.getElementNameWithoutExtension(target);
        element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(target), toImportQName + "." + targetName));
      }
      else {
        final String name = target.getName();
        if (name != null)
          addOrUpdateFromImportStatement(file, toImportQName.toString(), name, null, priority, element);
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
