package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.codeInsight.completion.OverwriteEqualsInsertHandler;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PyKeywordPatternReference extends PsiReferenceBase.Poly<PyKeywordPattern> {
  public PyKeywordPatternReference(@NotNull PyKeywordPattern keywordPattern) {
    super(keywordPattern, keywordPattern.getKeywordElement().getTextRangeInParent(), false);
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PyClassPattern classPattern = getContainingClassPattern();
    if (classPattern == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    PyKeywordPattern keywordPattern = getElement();
    TypeEvalContext typeContext = TypeEvalContext.codeInsightFallback(keywordPattern.getProject());
    PyResolveContext resolveContext = PyResolveContext.defaultContext(typeContext);
    return StreamEx.of(resolveToClassTypes(classPattern, resolveContext))
      .flatMap(t -> StreamEx.of(ContainerUtil.notNullize(t.resolveMember(keywordPattern.getKeyword(),
                                                                         null, AccessDirection.READ, resolveContext))))
      .toArray(ResolveResult.EMPTY_ARRAY);
  }

  @Override
  public Object @NotNull [] getVariants() {
    PyClassPattern classPattern = getContainingClassPattern();
    if (classPattern == null) {
      return LookupElement.EMPTY_ARRAY;
    }
    PyKeywordPattern keywordPattern = getElement();
    TypeEvalContext typeContext = TypeEvalContext.codeCompletion(keywordPattern.getProject(), keywordPattern.getContainingFile());
    return collectClassAttributeVariants(getElement(), classPattern, typeContext);
  }

  @Nullable
  private PyClassPattern getContainingClassPattern() {
    return as(getElement().getParent().getParent(), PyClassPattern.class);
  }

  static LookupElement @NotNull [] collectClassAttributeVariants(@NotNull PsiElement location,
                                                                 @NotNull PyClassPattern classPattern,
                                                                 @NotNull TypeEvalContext typeContext) {
    PyResolveContext resolveContext = PyResolveContext.defaultContext(typeContext);
    return StreamEx.of(resolveToClassTypes(classPattern, resolveContext))
      .flatMap(t -> StreamEx.of(t.getCompletionVariants("", location, new ProcessingContext())))
      .select(LookupElement.class)
      .filter(e -> isMeaningfulClassPatternAttribute(e))
      .map(e -> new KeywordAttributeLookupDecorator(e, location))
      .toArray(LookupElement.EMPTY_ARRAY);
  }

  private static boolean isMeaningfulClassPatternAttribute(@NotNull LookupElement lookupElement) {
    String lookupString = lookupElement.getLookupString();
    if (lookupString.contains(".") || PyUtil.isSpecialName(lookupString)) {
      return false;
    }
    PsiElement elem = lookupElement.getPsiElement();
    if (elem instanceof PyClass) {
      return false;
    }
    if (elem instanceof PyFunction && ((PyFunction)elem).getProperty() == null) {
      return false;
    }
    return true;
  }

  @NotNull
  private static List<PyClassLikeType> resolveToClassTypes(@NotNull PyClassPattern classPattern, @NotNull PyResolveContext resolveContext) {
    List<PsiElement> elements = PyUtil.multiResolveTopPriority(classPattern.getClassNameReference(), resolveContext);
    return StreamEx.of(elements)
      .select(PyClass.class)
      .map(e -> e.getType(resolveContext.getTypeEvalContext()))
      .nonNull()
      .map(PyInstantiableType::toInstance)
      .toList();
  }

  private static class KeywordAttributeLookupDecorator extends LookupElementDecorator<LookupElement> {
    private final boolean myAddSpacesAroundEq;

    private KeywordAttributeLookupDecorator(@NotNull LookupElement e, @NotNull PsiElement settingsAnchor) {
      super(e);
      myAddSpacesAroundEq = PythonCodeStyleService.getInstance().isSpaceAroundEqInKeywordArgument(settingsAnchor.getContainingFile());
    }

    @Override
    public @NotNull String getLookupString() {
      return super.getLookupString() + (myAddSpacesAroundEq ? " = " : "=");
    }

    @Override
    public Set<String> getAllLookupStrings() {
      return Collections.singleton(getLookupString());
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setItemText(getLookupString());
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      OverwriteEqualsInsertHandler.INSTANCE.handleInsert(context, this);
    }
  }
}
