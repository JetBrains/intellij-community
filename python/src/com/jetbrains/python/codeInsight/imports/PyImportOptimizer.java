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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import one.util.streamex.StreamEx;
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
    private static final Comparator<PyImportElement> IMPORT_ELEMENT_COMPARATOR = (o1, o2) -> {
      final int byImportedName = Comparing.compare(o1.getImportedQName(), o2.getImportedQName());
      if (byImportedName != 0) {
        return byImportedName;
      }
      return Comparing.compare(o1.getAsName(), o2.getAsName());
    };

    private final PyFile myFile;
    private final PyCodeStyleSettings myPySettings;
    private final List<PyImportStatementBase> myImportBlock;
    private final Map<ImportPriority, List<PyImportStatementBase>> myGroups;
    private final MultiMap<PyImportStatementBase, PsiComment> myImportToLineComments;
    private final MultiMap<PyImportStatementBase, PsiComment> myTransformedImportToTrailingComments;

    private ImportSorter(@NotNull PyFile file) {
      myFile = file;
      myPySettings = CodeStyleSettingsManager.getSettings(myFile.getProject()).getCustomSettings(PyCodeStyleSettings.class);
      myImportBlock = myFile.getImportBlock();
      myImportToLineComments = MultiMap.create();
      myTransformedImportToTrailingComments = MultiMap.create();
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

      final Project project = myFile.getProject();
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel langLevel = LanguageLevel.forElement(myFile);
      
      // Used to combine "from" imports with the same sources
      final MultiMap<String, PyFromImportStatement> fromImportSources = MultiMap.create();
      // Preserve line comments if any
      final MultiMap<PyImportStatementBase, PsiComment> precedingComments = MultiMap.create();
      final Map<PyImportStatementBase, PsiComment> trailingComments = new HashMap<>();
      
      for (PyImportStatementBase statement : imports) {
        final PyFromImportStatement fromImport = as(statement, PyFromImportStatement.class);
        if (fromImport != null && !fromImport.isStarImport()) {
          fromImportSources.putValue(getNormalizedFromImportSource(fromImport), fromImport);
        }
        precedingComments.putValues(statement, collectPrecedingLineComments(statement));
        ContainerUtil.putIfNotNull(statement, as(statement.getLastChild(), PsiComment.class), trailingComments);
      }
      
      for (PyImportStatementBase statement : imports) {
        if (statement instanceof PyImportStatement) {
          final PyImportStatement importStatement = (PyImportStatement)statement;
          final PyImportElement[] importElements = importStatement.getImportElements();
          // Split combined imports like "import foo, bar as b"
          if (importElements.length > 1) {
            final List<PyImportStatement> newImports = ContainerUtil.map(importElements, e -> generator.createImportStatement(langLevel, e.getText(), null));
            final PyImportStatement topmostImport;
            if (myPySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS) {
              topmostImport = Collections.min(newImports, AddImportHelper.getSameGroupImportsComparator(project));
            }
            else {
              topmostImport = newImports.get(0);
            }
            myImportToLineComments.putValues(topmostImport, precedingComments.get(statement));
            final PsiComment trailingComment = trailingComments.get(statement);
            if (trailingComment != null) {
              myTransformedImportToTrailingComments.putValue(topmostImport, trailingComment);
            }
            result.addAll(newImports);
          }
          else {
            myImportToLineComments.putValues(statement, precedingComments.get(statement));
            result.add(importStatement);
          }
        }
        else if (statement instanceof PyFromImportStatement) {
          final PyFromImportStatement fromImport = (PyFromImportStatement)statement;
          final String source = getNormalizedFromImportSource(fromImport);
          final List<PyImportElement> newStatementElements = new ArrayList<>();
          
          // We can neither sort, nor combine star imports
          if (!fromImport.isStarImport()) {
            final Collection<PyFromImportStatement> sameSourceImports = fromImportSources.get(source);
            if (sameSourceImports.isEmpty()) {
              continue;
            }

            // Join multiple "from" imports with the same source, like "from module import foo; from module import bar as b"
            if (myPySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE && sameSourceImports.size() > 1) {
              for (PyFromImportStatement sameSourceImport : sameSourceImports) {
                ContainerUtil.addAll(newStatementElements, sameSourceImport.getImportElements());
              }
              // Remember that we have checked imports with this source already 
              fromImportSources.remove(source);
            }
            else if (myPySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS) {
              final List<PyImportElement> originalElements = Arrays.asList(fromImport.getImportElements());
              if (!Ordering.from(IMPORT_ELEMENT_COMPARATOR).isOrdered(originalElements)) {
                ContainerUtil.addAll(newStatementElements, originalElements);
              }
            }
          }

          if (!newStatementElements.isEmpty()) {
            if (myPySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS) {
              Collections.sort(newStatementElements, IMPORT_ELEMENT_COMPARATOR);
            }
            final String importedNames = StringUtil.join(newStatementElements, PsiElement::getText, ", ");
            final PyFromImportStatement combinedImport = generator.createFromImportStatement(langLevel, source, importedNames, null);
            ContainerUtil.map2LinkedSet(newStatementElements, e -> (PyImportStatementBase)e.getParent()).forEach(affected -> {
              myImportToLineComments.putValues(combinedImport, precedingComments.get(affected));
              final PsiComment trailingComment = trailingComments.get(affected);
              if (trailingComment != null) {
                myTransformedImportToTrailingComments.putValue(combinedImport, trailingComment);
              }
            });
            result.add(combinedImport);
          }
          else {
            myImportToLineComments.putValues(fromImport, precedingComments.get(fromImport));
            result.add(fromImport);
          }
        }
      }
      return result;
    }

    @NotNull
    private static List<PsiComment> collectPrecedingLineComments(@NotNull PyImportStatementBase statement) {
      final List<PsiComment> result = new ArrayList<>();
      PsiElement prev = PyPsiUtils.getPrevNonWhitespaceSibling(statement);
      while ((prev instanceof PsiComment) && onItsOwnLine(prev) && !isShebangComment(((PsiComment)prev))) {
        result.add((PsiComment)prev);
        prev = PyPsiUtils.getPrevNonWhitespaceSibling(prev);
      }
      Collections.reverse(result);
      return result;
    }

    private static boolean isShebangComment(@NotNull PsiComment comment) {
      return comment.getTextRange().getStartOffset() == 0 && comment.getText().startsWith("#!");
    }

    private static boolean onItsOwnLine(@NotNull PsiElement element) {
      if (element.getTextRange().getStartOffset() == 0) return true;
      final PsiWhiteSpace sibling = as(PsiTreeUtil.prevLeaf(element), PsiWhiteSpace.class);
      return sibling != null && (sibling.textContains('\n') || sibling.getTextRange().getStartOffset() == 0);
    }

    @NotNull
    public static String getNormalizedFromImportSource(@NotNull PyFromImportStatement statement) {
      return StringUtil.repeatSymbol('.', statement.getRelativeLevel()) + Objects.toString(statement.getImportSourceQName(), "");
    }

    private boolean groupsNotSorted() {
      if (!myPySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS) {
        return false;
      }
      final Ordering<PyImportStatementBase> importOrdering = Ordering.from(AddImportHelper.getSameGroupImportsComparator(myFile.getProject()));
      return ContainerUtil.exists(myGroups.values(), imports -> !importOrdering.isOrdered(imports));
    }

    private boolean needBlankLinesBetweenGroups() {
      return StreamEx.of(myGroups.values()).remove(List::isEmpty).count() > 1;
    }

    private void applyResults() {
      if (myPySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS) {
        for (ImportPriority priority : myGroups.keySet()) {
          final List<PyImportStatementBase> imports = myGroups.get(priority);
          Collections.sort(imports, AddImportHelper.getSameGroupImportsComparator(myFile.getProject()));
          myGroups.put(priority, imports);
        }
      }
      final PyImportStatementBase firstImport = myImportBlock.get(0);
      final List<PsiComment> comments = collectPrecedingLineComments(firstImport);
      final PsiElement topmostAnchor = ObjectUtils.notNull(ContainerUtil.getFirstItem(comments), firstImport);
      addImportsBefore(topmostAnchor);
      myFile.deleteChildRange(topmostAnchor, ContainerUtil.getLastItem(myImportBlock));
    }

    private void addImportsBefore(@NotNull PsiElement anchor) {
      final StringBuilder content = new StringBuilder();
      
      for (List<PyImportStatementBase> imports : myGroups.values()) {
        if (content.length() > 0) {
          // one extra blank line between import groups according to PEP 8
          content.append("\n");
        }
        for (PyImportStatementBase statement : imports) {
          for (PsiComment comment : myImportToLineComments.get(statement)) {
            content.append(comment.getText()).append("\n");
          }
          content.append(statement.getText());
          final Collection<PsiComment> trailingComments = myTransformedImportToTrailingComments.get(statement);
          if (!trailingComments.isEmpty()) {
            content.append("  ");
            for (PsiComment comment : trailingComments) {
              content.append(comment.getText()).append("\n");
            }
          }
          else {
            content.append("\n");
          }
        }
      }
      
      final Project project = anchor.getProject();
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      PyFile file = (PyFile)generator.createDummyFile(LanguageLevel.forElement(anchor), content.toString());
      file = (PyFile)CodeStyleManager.getInstance(project).reformat(file);
      final List<PyImportStatementBase> newImportBlock = file.getImportBlock();
      assert newImportBlock != null;

      final PyImportStatementBase lastImport = ContainerUtil.getLastItem(newImportBlock);
      assert lastImport != null;
      myFile.addRangeBefore(file.getFirstChild(), lastImport, anchor);
    }
  }
}
