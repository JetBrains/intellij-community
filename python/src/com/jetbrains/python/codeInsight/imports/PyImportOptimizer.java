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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.formatter.PyBlock;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
      public void visitElement(PsiElement node) {
        super.visitElement(node);
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
    private final List<PyImportStatementBase> myImportBlock;
    private final Map<ImportPriority, List<PyImportStatementBase>> myGroups;

    private ImportSorter(@NotNull PyFile file) {
      myFile = file;
      myImportBlock = myFile.getImportBlock();
      myGroups = new EnumMap<ImportPriority, List<PyImportStatementBase>>(ImportPriority.class);
      for (ImportPriority priority : ImportPriority.values()) {
        myGroups.put(priority, new ArrayList<PyImportStatementBase>());
      }
    }

    public void run() {
      if (myImportBlock.isEmpty()) {
        return;
      }
      boolean hasSplittedImports = false;
      final LanguageLevel langLevel = LanguageLevel.forElement(myFile);
      final PyElementGenerator generator = PyElementGenerator.getInstance(myFile.getProject());
      for (PyImportStatementBase importStatement : myImportBlock) {
        final ImportPriority priority = AddImportHelper.getImportPriority(importStatement);
        if (importStatement instanceof PyImportStatement && importStatement.getImportElements().length > 1) {
          for (PyImportElement importElement : importStatement.getImportElements()) {
            hasSplittedImports = true;
            // getText() for ImportElement includes alias
            final PyImportStatement splitImport = generator.createImportStatement(langLevel, importElement.getText(), null);
            myGroups.get(priority).add(splitImport);
          }
        }
        else {
          myGroups.get(priority).add(importStatement);
        }
      }
      if (hasSplittedImports || needBlankLinesBetweenGroups() || groupsNotSorted()) {
        applyResults();
      }
    }

    private boolean groupsNotSorted() {
      final Ordering<PyImportStatementBase> importOrdering = Ordering.from(AddImportHelper.IMPORT_TYPE_THEN_NAME_COMPARATOR);
      return SORT_IMPORTS && ContainerUtil.exists(myGroups.values(), new Condition<List<PyImportStatementBase>>() {
        @Override
        public boolean value(List<PyImportStatementBase> imports) {
          return !importOrdering.isOrdered(imports);
        }
      });
    }

    private boolean needBlankLinesBetweenGroups() {
      int nonEmptyGroups = 0;
      for (List<PyImportStatementBase> bases : myGroups.values()) {
        if (!bases.isEmpty()) {
          nonEmptyGroups++;
        }
      }
      return nonEmptyGroups > 1;
    }

    private void applyResults() {
      if (SORT_IMPORTS) {
        for (ImportPriority priority : myGroups.keySet()) {
          final List<PyImportStatementBase> imports = myGroups.get(priority);
          Collections.sort(imports, AddImportHelper.IMPORT_TYPE_THEN_NAME_COMPARATOR);
          myGroups.put(priority, imports);
        }
      }
      markGroupStarts();
      addImports(myImportBlock.get(0));

      myFile.deleteChildRange(myImportBlock.get(0), myImportBlock.get(myImportBlock.size() - 1));
    }

    private void markGroupStarts() {
      for (List<PyImportStatementBase> group : myGroups.values()) {
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
      // EnumMap returns values in key order, i.e. according to import groups priority
      for (List<PyImportStatementBase> imports : myGroups.values()) {
        for (PyImportStatementBase newImport : imports) {
          myFile.addBefore(newImport, anchor);
        }
      }
    }
  }
}
