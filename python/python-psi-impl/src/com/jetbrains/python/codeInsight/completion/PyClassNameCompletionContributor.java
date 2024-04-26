// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Adds completion variants for Python classes, functions and variables.
 */
public final class PyClassNameCompletionContributor extends PyExtendedCompletionContributor {

  @Override
  protected void doFillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiFile originalFile = parameters.getOriginalFile();
    final PsiElement element = parameters.getPosition();
    final PsiElement parent = element.getParent();
    final ScopeOwner originalScope = ScopeUtil.getScopeOwner(parameters.getOriginalPosition());
    final Condition<PsiElement> fromAnotherScope = e -> ScopeUtil.getScopeOwner(e) != originalScope;

    addVariantsFromIndex(result,
                         originalFile,
                         PyClassNameIndex.KEY,
                         parent instanceof PyStringLiteralExpression ? getStringLiteralInsertHandler() : getImportingInsertHandler(),
                         // TODO: implement autocompletion for inner classes
                         Conditions.and(fromAnotherScope, PyUtil::isTopLevel),
                         PyClass.class,
                         createClassElementHandler(originalFile));

    addVariantsFromIndex(result,
                         originalFile,
                         PyFunctionNameIndex.KEY,
                         getFunctionInsertHandler(parent),
                         Conditions.and(fromAnotherScope, PyUtil::isTopLevel),
                         PyFunction.class,
                         Function.identity());

    addVariantsFromIndex(result,
                         originalFile,
                         PyVariableNameIndex.KEY,
                         parent instanceof PyStringLiteralExpression ? getStringLiteralInsertHandler() : getImportingInsertHandler(),
                         Conditions.and(fromAnotherScope, PyUtil::isTopLevel),
                         PyTargetExpression.class,
                         Function.identity());
  }

  @NotNull
  private static Function<LookupElement, LookupElement> createClassElementHandler(@NotNull PsiFile file) {
    final PyFile pyFile = PyUtil.as(file, PyFile.class);
    if (pyFile == null) return Function.identity();

    final Set<QualifiedName> sourceQNames =
      ContainerUtil.map2SetNotNull(pyFile.getFromImports(), PyFromImportStatement::getImportSourceQName);

    return le -> {
      final PyClass cls = PyUtil.as(le.getPsiElement(), PyClass.class);
      if (cls == null) return le;

      final String clsQName = cls.getQualifiedName();
      if (clsQName == null) return le;

      if (!sourceQNames.contains(QualifiedName.fromDottedString(clsQName).removeLastComponent())) return le;

      return PrioritizedLookupElement.withPriority(le, PythonCompletionWeigher.PRIORITY_WEIGHT);
    };
  }

  private InsertHandler<LookupElement> getFunctionInsertHandler(PsiElement parent) {
    if (parent instanceof PyStringLiteralExpression) {
      return getStringLiteralInsertHandler();
    }
    if (parent.getParent() instanceof PyDecorator) {
      return getImportingInsertHandler();
    }
    return getFunctionInsertHandler();
  }

  private static <T extends PsiNamedElement> void addVariantsFromIndex(@NotNull CompletionResultSet resultSet,
                                                                       @NotNull PsiFile targetFile,
                                                                       @NotNull StubIndexKey<String, T> indexKey,
                                                                       @NotNull InsertHandler<LookupElement> insertHandler,
                                                                       @NotNull Condition<? super T> condition,
                                                                       @NotNull Class<T> elementClass,
                                                                       @NotNull Function<LookupElement, LookupElement> elementHandler) {
    final Project project = targetFile.getProject();
    final GlobalSearchScope scope = PySearchUtilBase.defaultSuggestionScope(targetFile);
    final Set<String> alreadySuggested = new HashSet<>();

    StubIndex stubIndex = StubIndex.getInstance();
    final Collection<String> allKeys = stubIndex.getAllKeys(indexKey, project);
    for (String elementName : resultSet.getPrefixMatcher().sortMatching(allKeys)) {
      stubIndex.processElements(indexKey, elementName, project, scope, elementClass, (element) -> {
        ProgressManager.checkCanceled();
        if (!condition.value(element)) return true;
        String name = element.getName();
        if (name == null) return true;
        QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(element, targetFile);
        if (importPath == null) return true;
        String qualifiedName = importPath + "." + name;
        if (alreadySuggested.add(qualifiedName)) {
          LookupElementBuilder lookupElement = LookupElementBuilder
            .createWithSmartPointer(name, element)
            .withIcon(element.getIcon(0))
            .withTailText(" (" + importPath + ")", true)
            .withInsertHandler(insertHandler);
          resultSet.addElement(elementHandler.apply(lookupElement));
        }
        return true;
      });
    }
  }
}
