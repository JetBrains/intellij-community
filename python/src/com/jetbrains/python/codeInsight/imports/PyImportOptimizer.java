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

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.lang.ImportOptimizer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.formatter.PyBlock;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyImportOptimizer implements ImportOptimizer {
  @Override
  public boolean supports(PsiFile file) {
    return true;
  }

  @Override
  @NotNull
  public Runnable processFile(@NotNull final PsiFile file) {
    final LocalInspectionToolSession session = new LocalInspectionToolSession(file, 0, file.getTextLength());
    final PyUnresolvedReferencesInspection.Visitor visitor = new PyUnresolvedReferencesInspection.Visitor(null,
                                                                                                          session,
                                                                                                          Collections.<String>emptyList());
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyElement(PyElement node) {
        super.visitPyElement(node);
        node.accept(visitor);
      }
    });
    return new Runnable() {
      @Override
      public void run() {
        visitor.optimizeImports();
        if (file instanceof PyFile) {
          new ImportSorter((PyFile)file).run();
        }
      }
    };
  }

  private static class ImportSorter {

    private final PyFile myFile;
    private final List<PyImportStatementBase> myBuiltinImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myThirdPartyImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myProjectImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myImportBlock;
    private final PyElementGenerator myGenerator;
    private boolean myMissorted = false;

    private ImportSorter(PyFile file) {
      myFile = file;
      myImportBlock = myFile.getImportBlock();
      myGenerator = PyElementGenerator.getInstance(myFile.getProject());
    }

    public void run() {
      if (myImportBlock.isEmpty()) {
        return;
      }
      LanguageLevel langLevel = LanguageLevel.forElement(myFile);
      for (PyImportStatementBase importStatement : myImportBlock) {
        if (importStatement instanceof PyFromImportStatement && ((PyFromImportStatement)importStatement).isFromFuture()) {
          continue;
        }
        if (importStatement instanceof PyImportStatement && importStatement.getImportElements().length > 1) {
          for (PyImportElement importElement : importStatement.getImportElements()) {
            myMissorted = true;
            // getText() for ImportElement includes alias
            final PyImportStatement splitImport = myGenerator.createImportStatement(langLevel, importElement.getText(), null);
            prioritize(splitImport, importElement.resolve());
          }
        }
        else {
          final PsiElement toImport;
          if (importStatement instanceof PyFromImportStatement) {
            toImport = ((PyFromImportStatement)importStatement).resolveImportSource();
          }
          else {
            final PyImportElement firstImportElement = ArrayUtil.getFirstElement(importStatement.getImportElements());
            toImport = firstImportElement != null ? firstImportElement.resolve() : null;
          }
          prioritize(importStatement, toImport);
        }
      }
      if (myMissorted || needBlankLinesBetweenGroups()) {
        applyResults();
      }
    }

    private boolean needBlankLinesBetweenGroups() {
      int nonEmptyGroups = 0;
      if (myBuiltinImports.size() > 0) nonEmptyGroups++;
      if (myThirdPartyImports.size() > 0) nonEmptyGroups++;
      if (myProjectImports.size() > 0) nonEmptyGroups++;
      return nonEmptyGroups > 1;
    }

    private void prioritize(PyImportStatementBase importStatement, @Nullable PsiElement toImport) {
      if (toImport != null && !(toImport instanceof PsiFileSystemItem)) {
        toImport = toImport.getContainingFile();
      }
      final AddImportHelper.ImportPriority priority = toImport == null
                                                      ? AddImportHelper.ImportPriority.PROJECT
                                                      : AddImportHelper.getImportPriority(myFile, (PsiFileSystemItem)toImport);
      if (priority == AddImportHelper.ImportPriority.BUILTIN) {
        myBuiltinImports.add(importStatement);
        if (!myThirdPartyImports.isEmpty() || !myProjectImports.isEmpty()) {
          myMissorted = true;
        }
      }
      else if (priority == AddImportHelper.ImportPriority.THIRD_PARTY) {
        myThirdPartyImports.add(importStatement);
        if (!myProjectImports.isEmpty()) {
          myMissorted = true;
        }
      }
      else {
        myProjectImports.add(importStatement);
      }
    }

    private void applyResults() {
      Collections.sort(myBuiltinImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
      Collections.sort(myThirdPartyImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
      Collections.sort(myProjectImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);

      markGroupBegin(myThirdPartyImports);
      markGroupBegin(myProjectImports);

      addImports(myBuiltinImports);
      addImports(myThirdPartyImports);
      addImports(myProjectImports);
      final PsiElement lastElement = myImportBlock.get(myImportBlock.size() - 1);
      final PyImportStatementBase firstNonFutureImport = findFirstNonFutureImport();
      if (firstNonFutureImport != null) {
        myFile.deleteChildRange(firstNonFutureImport, lastElement);
      }
      for (PyImportStatementBase anImport : myBuiltinImports) {
        anImport.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, null);
      }
    }

    private PyImportStatementBase findFirstNonFutureImport() {
      for (PyImportStatementBase importStatement : myImportBlock) {
        if (!(importStatement instanceof PyFromImportStatement && ((PyFromImportStatement)importStatement).isFromFuture())) {
          return importStatement;
        }
      }
      return null;
    }

    private static void markGroupBegin(@NotNull List<PyImportStatementBase> imports) {
      if (imports.size() > 0) {
        imports.get(0).putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, true);
      }
    }

    private void addImports(final List<PyImportStatementBase> imports) {
      for (PyImportStatementBase newImport : imports) {
        myFile.addBefore(newImport, findFirstNonFutureImport());
      }
    }
  }
}
