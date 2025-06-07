// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.google.common.collect.Ordering;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.inspections.PyUnusedImportsInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;


public final class PyImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance(PyImportOptimizer.class);
  private static final Set<JavaResourceRootType> TEST_RESOURCE_ROOT_TYPES = Set.of(JavaResourceRootType.TEST_RESOURCE);

  private boolean mySortImports = true;

  public static @NotNull PyImportOptimizer onlyRemoveUnused() {
    final PyImportOptimizer optimizer = new PyImportOptimizer();
    optimizer.mySortImports = false;
    return optimizer;
  }

  @Override
  public boolean supports(@NotNull PsiFile file) {
    return true;
  }

  @Override
  public @NotNull Runnable processFile(final @NotNull PsiFile file) {
    if (isInsideTestResourceRoot(file)) {
      return EmptyRunnable.INSTANCE;
    }

    PyUnusedImportsInspection.Visitor visitor = prepare(file);
    return () -> {
      LOG.debug(String.format("----------------- OPTIMIZE IMPORTS STARTED (%s) -----------------", file.getVirtualFile()));
      visitor.optimizeImports();
      if (mySortImports && file instanceof PyFile) {
        new ImportSorter((PyFile)file).run();
      }
      LOG.debug("----------------- OPTIMIZE IMPORTS FINISHED -----------------");
    };
  }

  private PyUnusedImportsInspection.Visitor prepare(@NotNull PsiFile file) {
    final PsiFile contextFile = FileContextUtil.getContextFile(file);
    final PsiFile rfile = ObjectUtils.chooseNotNull(contextFile, file);

    TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), rfile);

    PyUnusedImportsInspection inspection = new PyUnusedImportsInspection();
    PyUnusedImportsInspection.Visitor visitor = new PyUnusedImportsInspection.Visitor(
      null, inspection, context, PythonLanguageLevelPusher.getLanguageLevelForFile(file)
    );
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement node) {
        super.visitElement(node);
        node.accept(visitor);
      }
    });
    return visitor;
  }

  private static boolean isInsideTestResourceRoot(@NotNull PsiFile file) {
    return ProjectFileIndex.getInstance(file.getProject()).isUnderSourceRootOfType(file.getVirtualFile(), TEST_RESOURCE_ROOT_TYPES);
  }

  private static final class ImportSorter {
    private final PyFile myFile;
    private final PyCodeStyleSettings myPySettings;
    private final List<PyImportStatementBase> myImportBlock;
    private final Map<ImportPriority, List<PyImportStatementBase>> myGroups;

    private final MultiMap<PyImportStatementBase, PsiComment> myOldImportToLineComments = MultiMap.create();
    private final MultiMap<PyImportStatementBase, PsiComment> myOldImportToInnerComments = MultiMap.create();
    private final MultiMap<String, PyFromImportStatement> myOldFromImportBySources = MultiMap.create();

    private final MultiMap<PyImportStatementBase, PsiComment> myNewImportToLineComments = MultiMap.create();
    // Contains trailing and nested comments of modified (split and joined) imports
    private final MultiMap<PyImportStatementBase, PsiComment> myNewImportToInnerComments = MultiMap.create();
    private final List<PsiComment> myDanglingComments = new ArrayList<>();
    private final PyElementGenerator myGenerator;
    private final LanguageLevel myLangLevel;

    private ImportSorter(@NotNull PyFile file) {
      myFile = file;
      myPySettings = CodeStyle.getCustomSettings(myFile, PyCodeStyleSettings.class);
      myImportBlock = myFile.getImportBlock();
      myGroups = new EnumMap<>(ImportPriority.class);
      for (ImportPriority priority : ImportPriority.values()) {
        myGroups.put(priority, new ArrayList<>());
      }
      myGenerator = PyElementGenerator.getInstance(myFile.getProject());
      myLangLevel = LanguageLevel.forElement(myFile);
    }

    private @NotNull Comparator<PyImportElement> getFromNamesComparator() {
      final Comparator<String> stringComparator = AddImportHelper.getImportTextComparator(myFile);
      final Comparator<QualifiedName> qNamesComparator = Comparator.comparing(QualifiedName::toString, stringComparator);
      return Comparator
        .comparing(PyImportElement::getImportedQName, Comparator.nullsFirst(qNamesComparator))
        .thenComparing(PyImportElement::getAsName, Comparator.nullsFirst(stringComparator));
    }

    public void run() {
      if (myImportBlock.isEmpty()) {
        return;
      }

      analyzeImports(myImportBlock);

      for (PyImportStatementBase importStatement : myImportBlock) {
        final AddImportHelper.ImportPriorityChoice choice = AddImportHelper.getImportPriorityWithReason(importStatement);
        LOG.debug(String.format("Import group for '%s' is %s: %s",
                                importStatement.getText(), choice.getPriority(), choice.getDescription()));
        myGroups.get(choice.getPriority()).add(importStatement);
      }

      boolean hasTransformedImports = false;
      for (ImportPriority priority : ImportPriority.values()) {
        final List<PyImportStatementBase> original = myGroups.get(priority);
        final List<PyImportStatementBase> transformed = transformImportsInGroup(original);
        hasTransformedImports |= !original.equals(transformed);
        myGroups.put(priority, transformed);
      }

      if (hasTransformedImports || needBlankLinesBetweenGroups() || groupsNotSorted()) {
        applyResults();
      }
    }

    private void analyzeImports(@NotNull List<PyImportStatementBase> imports) {
      for (PyImportStatementBase statement : imports) {
        final PyFromImportStatement fromImport = as(statement, PyFromImportStatement.class);
        if (fromImport != null && !fromImport.isStarImport()) {
          myOldFromImportBySources.putValue(getNormalizedFromImportSource(fromImport), fromImport);
        }
        final Couple<List<PsiComment>> boundAndOthers = collectPrecedingLineComments(statement);
        myOldImportToLineComments.putValues(statement, boundAndOthers.getFirst());
        if (statement != myImportBlock.get(0)) {
          myDanglingComments.addAll(boundAndOthers.getSecond());
        }
        myOldImportToInnerComments.putValues(statement, PsiTreeUtil.collectElementsOfType(statement, PsiComment.class));
      }
    }

    private @NotNull List<PyImportStatementBase> transformImportsInGroup(@NotNull List<PyImportStatementBase> imports) {
      final List<PyImportStatementBase> result = new ArrayList<>();

      for (PyImportStatementBase statement : imports) {
        if (statement instanceof PyImportStatement) {
          transformPlainImport(result, (PyImportStatement)statement);
        }
        else if (statement instanceof PyFromImportStatement) {
          transformFromImport(result, (PyFromImportStatement)statement);
        }
      }
      return result;
    }

    private void transformPlainImport(@NotNull List<PyImportStatementBase> result, @NotNull PyImportStatement importStatement) {
      final PyImportElement[] importElements = importStatement.getImportElements();
      // Split combined imports like "import foo, bar as b"
      if (importElements.length > 1) {
        final List<PyImportStatement> newImports =
          ContainerUtil.map(importElements, e -> myGenerator.createImportStatement(myLangLevel, e.getText(), null));
        replaceOneImportWithSeveral(result, importStatement, newImports);
      }
      else {
        addImportAsIs(result, importStatement);
      }
    }

    private void transformFromImport(@NotNull List<PyImportStatementBase> result, @NotNull PyFromImportStatement fromImport) {
      // We can neither sort, nor combine star imports
      if (fromImport.isStarImport()) {
        addImportAsIs(result, fromImport);
        return;
      }

      final String source = getNormalizedFromImportSource(fromImport);
      final PyImportElement[] importedFromNames = fromImport.getImportElements();

      final List<PyImportElement> newFromImportNames = new ArrayList<>();
      final Comparator<PyImportElement> fromNamesComparator = getFromNamesComparator();

      final Collection<PyFromImportStatement> sameSourceImports = myOldFromImportBySources.get(source);
      if (sameSourceImports.isEmpty()) {
        return;
      }

      // Keep existing parentheses if we only re-order names inside the import
      boolean forceParentheses = sameSourceImports.size() == 1 && fromImport.getLeftParen() != null;

      // Join multiple "from" imports with the same source, like "from module import foo; from module import bar as b"
      final boolean shouldJoinImports = myPySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE && sameSourceImports.size() > 1;
      final boolean shouldSplitImport = myPySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS && importedFromNames.length > 1;

      if (shouldJoinImports) {
        for (PyFromImportStatement sameSourceImport : sameSourceImports) {
          ContainerUtil.addAll(newFromImportNames, sameSourceImport.getImportElements());
        }
        // Remember that we have checked imports with this source already
        myOldFromImportBySources.remove(source);
      }
      else if (!shouldSplitImport && myPySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS) {
        if (!Ordering.from(fromNamesComparator).isOrdered(Arrays.asList(importedFromNames))) {
          ContainerUtil.addAll(newFromImportNames, importedFromNames);
        }
      }

      final boolean shouldGenerateNewFromImport = !newFromImportNames.isEmpty();
      if (shouldGenerateNewFromImport) {
        if (myPySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS) {
          newFromImportNames.sort(fromNamesComparator);
        }
        String importedNames = StringUtil.join(newFromImportNames, ImportSorter::getNormalizedImportElementText, ", ");
        if (forceParentheses) {
          importedNames = "(" + importedNames + ")";
        }
        final PyFromImportStatement combinedImport = myGenerator.createFromImportStatement(myLangLevel, source, importedNames, null);
        final Set<PyImportStatementBase> oldImports = ContainerUtil.map2LinkedSet(newFromImportNames,
                                                                                  e -> (PyImportStatementBase)e.getParent());
        replaceSeveralImportsWithOne(result, oldImports, combinedImport);
      }
      else if (shouldSplitImport) {
        final List<PyFromImportStatement> newFromImports = ContainerUtil.map(importedFromNames, importElem -> {
          final String name = Objects.toString(importElem.getImportedQName(), "");
          final String alias = importElem.getAsName();
          return myGenerator.createFromImportStatement(myLangLevel, source, name, alias);
        });
        replaceOneImportWithSeveral(result, fromImport, newFromImports);
      }
      else {
        addImportAsIs(result, fromImport);
      }
    }

    private void replaceSeveralImportsWithOne(@NotNull List<PyImportStatementBase> result,
                                              @NotNull Collection<? extends PyImportStatementBase> oldImports,
                                              @NotNull PyFromImportStatement newImport) {
      for (PyImportStatementBase replaced : oldImports) {
        myNewImportToLineComments.putValues(newImport, myOldImportToLineComments.get(replaced));
        myNewImportToInnerComments.putValues(newImport, myOldImportToInnerComments.get(replaced));
      }
      result.add(newImport);
    }

    private void replaceOneImportWithSeveral(@NotNull List<PyImportStatementBase> result,
                                             @NotNull PyImportStatementBase oldImport,
                                             @NotNull Collection<? extends PyImportStatementBase> newImports) {
      final PyImportStatementBase topmostImport;
      if (myPySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS) {
        topmostImport = Collections.min(newImports, AddImportHelper.getSameGroupImportsComparator(myFile));
      }
      else {
        topmostImport = ContainerUtil.getFirstItem(newImports);
      }
      myNewImportToLineComments.putValues(topmostImport, myOldImportToLineComments.get(oldImport));
      myNewImportToInnerComments.putValues(topmostImport, myOldImportToInnerComments.get(oldImport));
      result.addAll(newImports);
    }

    private void addImportAsIs(@NotNull List<PyImportStatementBase> result, @NotNull PyImportStatementBase oldImport) {
      myNewImportToLineComments.putValues(oldImport, myOldImportToLineComments.get(oldImport));
      result.add(oldImport);
    }

    private static @NotNull String getNormalizedImportElementText(@NotNull PyImportElement element) {
      // Remove comments, line feeds and backslashes
      return element.getText().replaceAll("#.*", "").replaceAll("[\\s\\\\]+", " ");
    }

    private static @NotNull Couple<List<PsiComment>> collectPrecedingLineComments(@NotNull PyImportStatementBase statement) {
      final List<PsiComment> boundComments = PyPsiUtils.getPrecedingComments(statement, true);
      final PsiComment firstComment = ContainerUtil.getFirstItem(boundComments);
      if (firstComment != null && isFirstInFile(firstComment)) {
        return Couple.of(Collections.emptyList(), boundComments);
      }

      final List<PsiComment> remainingComments = PyPsiUtils.getPrecedingComments(ObjectUtils.notNull(firstComment, statement), false);
      return Couple.of(boundComments, remainingComments);
    }

    private static boolean isFirstInFile(@NotNull PsiElement element) {
      if (element.getTextRange().getStartOffset() == 0) return true;
      final PsiWhiteSpace prevWhitespace = as(PsiTreeUtil.prevLeaf(element), PsiWhiteSpace.class);
      return prevWhitespace != null && prevWhitespace.getTextRange().getStartOffset() == 0;
    }

    public static @NotNull String getNormalizedFromImportSource(@NotNull PyFromImportStatement statement) {
      return StringUtil.repeatSymbol('.', statement.getRelativeLevel()) + Objects.toString(statement.getImportSourceQName(), "");
    }

    private boolean groupsNotSorted() {
      if (!myPySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS) {
        return false;
      }
      final Ordering<PyImportStatementBase> importOrdering =
        Ordering.from(AddImportHelper.getSameGroupImportsComparator(myFile));
      return ContainerUtil.exists(myGroups.values(), imports -> !importOrdering.isOrdered(imports));
    }

    private boolean needBlankLinesBetweenGroups() {
      return StreamEx.of(myGroups.values()).remove(List::isEmpty).count() > 1;
    }

    private void applyResults() {
      if (myPySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS) {
        for (ImportPriority priority : myGroups.keySet()) {
          final List<PyImportStatementBase> imports = myGroups.get(priority);
          imports.sort(AddImportHelper.getSameGroupImportsComparator(myFile));
          myGroups.put(priority, imports);
        }
      }
      final PyImportStatementBase firstImport = myImportBlock.get(0);
      final List<PsiComment> boundComments = collectPrecedingLineComments(firstImport).getFirst();
      final PsiElement firstElementToRemove = boundComments.isEmpty() ? firstImport : boundComments.get(0);
      final PyImportStatementBase lastImport = ContainerUtil.getLastItem(myImportBlock);
      assert lastImport != null;
      addImportsAfter(lastImport);
      deleteRangeThroughDocument(firstElementToRemove, PyPsiUtils.getNextNonWhitespaceSibling(lastImport).getPrevSibling());
    }

    private void addImportsAfter(@NotNull PsiElement anchor) {
      final StringBuilder content = new StringBuilder();

      for (List<PyImportStatementBase> imports : myGroups.values()) {
        if (!content.isEmpty() && !imports.isEmpty()) {
          // one extra blank line between import groups according to PEP 8
          content.append("\n");
        }
        for (PyImportStatementBase statement : imports) {
          for (PsiComment comment : myNewImportToLineComments.get(statement)) {
            content.append(comment.getText()).append("\n");
          }
          content.append(statement.getText());
          final Collection<PsiComment> innerComments = myNewImportToInnerComments.get(statement);
          if (!innerComments.isEmpty()) {
            content.append("  # ");
            // Join several comments for the same import statement by ";" as isort does
            final String combinedComment = StringUtil.join(innerComments, comment -> comment.getText().substring(1).trim(), "; ");
            content.append(combinedComment).append("\n");
          }
          else {
            content.append("\n");
          }
        }
      }

      if (!myDanglingComments.isEmpty()) {
        content.append("\n");
        for (PsiComment comment : myDanglingComments) {
          content.append(comment.getText()).append("\n");
        }
      }

      final Project project = anchor.getProject();
      final PyFile file = (PyFile)myGenerator.createDummyFile(myLangLevel, content.toString());
      final PyFile reformattedFile = (PyFile)CodeStyleManager.getInstance(project).reformat(file);
      final List<PyImportStatementBase> newImportBlock = reformattedFile.getImportBlock();
      assert newImportBlock != null;

      myFile.addRangeAfter(reformattedFile.getFirstChild(), reformattedFile.getLastChild(), anchor);
    }

    private static void deleteRangeThroughDocument(@NotNull PsiElement first, @NotNull PsiElement last) {
      PyUtil.updateDocumentUnblockedAndCommitted(first, document -> {
        document.deleteString(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
      });
    }
  }
}
