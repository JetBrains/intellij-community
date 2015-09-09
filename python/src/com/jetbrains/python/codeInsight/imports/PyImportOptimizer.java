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

import com.google.common.collect.Ordering;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.lang.ImportOptimizer;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.formatter.PyBlock;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyImportOptimizer implements ImportOptimizer {
  private static final boolean SORT_IMPORTS = true;

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
    private final List<PyImportStatementBase> myFutureImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myBuiltinImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myThirdPartyImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myProjectImports = new ArrayList<PyImportStatementBase>();
    private final List<PyImportStatementBase> myImportBlock;
    private final PyElementGenerator myGenerator;

    private ImportSorter(PyFile file) {
      myFile = file;
      myImportBlock = myFile.getImportBlock();
      myGenerator = PyElementGenerator.getInstance(myFile.getProject());
    }

    public void run() {
      if (myImportBlock.isEmpty()) {
        return;
      }
      boolean hasSplittedImports = false;
      LanguageLevel langLevel = LanguageLevel.forElement(myFile);
      for (PyImportStatementBase importStatement : myImportBlock) {
        final ImportPriority priority = AddImportHelper.getImportPriority(importStatement);
        if (importStatement instanceof PyImportStatement && importStatement.getImportElements().length > 1) {
          for (PyImportElement importElement : importStatement.getImportElements()) {
            hasSplittedImports = true;
            // getText() for ImportElement includes alias
            final PyImportStatement splitImport = myGenerator.createImportStatement(langLevel, importElement.getText(), null);
            prioritize(splitImport, priority);
          }
        }
        else {
          prioritize(importStatement, priority);
        }
      }
      if (hasSplittedImports || needBlankLinesBetweenGroups() || groupsNotSorted()) {
        applyResults();
      }
    }

    private void prioritize(PyImportStatementBase importStatement, @NotNull ImportPriority priority) {
      if (priority == ImportPriority.FUTURE) {
        myFutureImports.add(importStatement);
      }
      else if (priority == ImportPriority.BUILTIN) {
        myBuiltinImports.add(importStatement);
      }
      else if (priority == ImportPriority.THIRD_PARTY) {
        myThirdPartyImports.add(importStatement);
      }
      else if (priority == ImportPriority.PROJECT) {
        myProjectImports.add(importStatement);
      }
    }

    private boolean groupsNotSorted() {
      final Ordering<PyImportStatementBase> importOrdering = Ordering.from(AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
      return SORT_IMPORTS && (!importOrdering.isOrdered(myFutureImports) || 
                              !importOrdering.isOrdered(myBuiltinImports) ||
                              !importOrdering.isOrdered(myProjectImports) ||
                              !importOrdering.isOrdered(myThirdPartyImports));
    }

    private boolean needBlankLinesBetweenGroups() {
      int nonEmptyGroups = 0;
      if (!myFutureImports.isEmpty()) nonEmptyGroups++;
      if (!myBuiltinImports.isEmpty()) nonEmptyGroups++;
      if (!myThirdPartyImports.isEmpty()) nonEmptyGroups++;
      if (!myProjectImports.isEmpty()) nonEmptyGroups++;
      return nonEmptyGroups > 1;
    }

    private void applyResults() {
      if (SORT_IMPORTS) {
        Collections.sort(myFutureImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
        Collections.sort(myBuiltinImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
        Collections.sort(myThirdPartyImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
        Collections.sort(myProjectImports, AddImportHelper.IMPORT_BY_NAME_COMPARATOR);
      }
      markGroupStarts();
      addImports(myImportBlock.get(0));

      myFile.deleteChildRange(myImportBlock.get(0), myImportBlock.get(myImportBlock.size() - 1));
    }

    private void markGroupStarts() {
      for (List<PyImportStatementBase> group : getImportGroupsInOrder()) {
        boolean firstImportInGroup = true;
        for (PyImportStatementBase statement : group) {
          if (firstImportInGroup) {
            statement.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, true);
            firstImportInGroup = false;
          }
          else {
            statement.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, null);
          }
        }
      }
    }

    private void addImports(@NotNull PyImportStatementBase anchor) {
      for (List<PyImportStatementBase> imports : getImportGroupsInOrder()) {
        for (PyImportStatementBase newImport : imports) {
          myFile.addBefore(newImport, anchor);
        }
      }
    }

    @NotNull
    private List<List<PyImportStatementBase>> getImportGroupsInOrder() {
      return Arrays.asList(myFutureImports, myBuiltinImports, myThirdPartyImports, myProjectImports);
    }
  }
}
