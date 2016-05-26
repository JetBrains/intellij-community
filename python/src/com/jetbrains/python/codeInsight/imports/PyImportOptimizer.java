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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.formatter.PyBlock;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

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
                                                                                                          Collections.emptyList());
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement node) {
        super.visitElement(node);
        node.accept(visitor);
      }
    });
    return () -> {
      visitor.optimizeImports();
      if (file instanceof PyFile) {
        new ImportSorter((PyFile)file).run();
      }
    };
  }

  private static class ImportSorter {

    private static final Comparator<PyImportElement> IMPORT_ELEMENT_COMPARATOR = (o1, o2) -> Comparing.compare(o1.getImportedQName(),
                                                                                                               o2.getImportedQName());

    private final PyFile myFile;
    private final List<PyImportStatementBase> myImportBlock;
    private final Map<ImportPriority, List<PyImportStatementBase>> myGroups;
    private final PyCodeStyleSettings myPySettings;

    private ImportSorter(@NotNull PyFile file) {
      myFile = file;
      myPySettings = CodeStyleSettingsManager.getSettings(myFile.getProject()).getCustomSettings(PyCodeStyleSettings.class);
      myImportBlock = myFile.getImportBlock();
      myGroups = new EnumMap<>(ImportPriority.class);
      for (ImportPriority priority : ImportPriority.values()) {
        myGroups.put(priority, new ArrayList<>());
      }
    }

    public void run() {
      if (myImportBlock.isEmpty()) {
        return;
      }
      
      for (PyImportStatementBase importStatement : myImportBlock) {
        final ImportPriority priority = AddImportHelper.getImportPriority(importStatement);
        myGroups.get(priority).add(importStatement);
      }
      
      boolean hasTransformedImports = false;
      for (ImportPriority priority : ImportPriority.values()) {
        final List<PyImportStatementBase> original = myGroups.get(priority);
        final List<PyImportStatementBase> transformed = transformImportStatements(original);
        hasTransformedImports |= !original.equals(transformed);
        myGroups.put(priority, transformed);
      }
      
      if (hasTransformedImports || needBlankLinesBetweenGroups() || groupsNotSorted()) {
        applyResults();
      }
    }
    
    @NotNull
    private List<PyImportStatementBase> transformImportStatements(@NotNull List<PyImportStatementBase> imports) {
      final List<PyImportStatementBase> result = new ArrayList<>();
      
      final PyElementGenerator generator = PyElementGenerator.getInstance(myFile.getProject());
      final LanguageLevel langLevel = LanguageLevel.forElement(myFile);
      
      final MultiMap<QualifiedName, PyFromImportStatement> fromImportSources = MultiMap.create();
      for (PyImportStatementBase statement : imports) {
        final PyFromImportStatement fromImport = as(statement, PyFromImportStatement.class);
        if (fromImport != null) {
          fromImportSources.putValue(fromImport.getImportSourceQName(), fromImport);
        }
      }

      for (PyImportStatementBase statement : imports) {
        if (statement instanceof PyImportStatement) {
          final PyImportStatement importStatement = (PyImportStatement)statement;
          final PyImportElement[] importElements = importStatement.getImportElements();
          // Split combined imports like "import foo, bar as b"
          if (importElements.length > 1) {
            for (PyImportElement importElement : importElements) {
              // getText() for ImportElement includes alias
              final PyImportStatement splitted = generator.createImportStatement(langLevel, importElement.getText(), null);
              result.add(splitted);
            }
          }
          else {
            result.add(importStatement);
          }
        }
        else if (statement instanceof PyFromImportStatement) {
          final PyFromImportStatement fromImportStatement = (PyFromImportStatement)statement;
          final QualifiedName source = fromImportStatement.getImportSourceQName();
          final String sourceText = Objects.toString(source, "");
          if (myPySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE) {
            final Collection<PyFromImportStatement> sameSourceImports = fromImportSources.get(source);
            if (!sameSourceImports.isEmpty()) {
              final List<PyImportElement> allImportElements = new ArrayList<>();
              for (PyFromImportStatement sameSourceImport : sameSourceImports) {
                ContainerUtil.addAll(allImportElements, sameSourceImport.getImportElements());
              }
              if (myPySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS) {
                Collections.sort(allImportElements, IMPORT_ELEMENT_COMPARATOR);
              }
              final String importedNames = StringUtil.join(allImportElements, PsiElement::getText, ", ");
              result.add(generator.createFromImportStatement(langLevel, sourceText, importedNames, null));
  
              // remember that we have checked imports from this source already 
              fromImportSources.remove(source);
            }
          }
          else if (myPySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS) {
            final PyImportElement[] importElements = fromImportStatement.getImportElements();
            Arrays.sort(importElements, IMPORT_ELEMENT_COMPARATOR);
            final String importedNames = StringUtil.join(importElements, PsiElement::getText, ", ");
            result.add(generator.createFromImportStatement(langLevel, sourceText, importedNames, null));
          }
          else {
            result.add(fromImportStatement);
          }
        }
      }
      
      
      return result;
    }

    private boolean groupsNotSorted() {
      if (!myPySettings.OPTIMIZE_IMPORTS_SORT_ALPHABETICALLY) {
        return false;
      }
      final Ordering<PyImportStatementBase> importOrdering = Ordering.from(AddImportHelper.IMPORT_TYPE_THEN_NAME_COMPARATOR);
      return ContainerUtil.exists(myGroups.values(), imports -> !importOrdering.isOrdered(imports));
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
      if (myPySettings.OPTIMIZE_IMPORTS_SORT_ALPHABETICALLY) {
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
