// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.lang.properties.template.PropertiesContextType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.handlers.LiteralWithSubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class PropertiesStructuralSearchProfile extends StructuralSearchProfile {

  private static final String TYPED_VAR_PREFIX = "__$_";

  @Override
  public Configuration @NotNull [] getPredefinedTemplates() {
    return new Configuration[] {
      createConfiguration(
        PropertiesBundle.message("predefined.configuration.duplicated.word.in.property.value"),
        "Duplicated word",
        "'_key='value:.*\\b(\\w+)\\s+\\1\\b.*"),

      createConfiguration(
        PropertiesBundle.message("predefined.configuration.double.single.quote.in.value.without.curly.brace"),
        "Double ' without {",
        "'_key='value:[ regex( [^{]*''[^{]* ) ]")
    };
  }

  @NotNull
  private static Configuration createConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                  @NotNull @NonNls String refName,
                                                  @NotNull @NonNls String criteria) {
    return PredefinedConfigurationUtil.createConfiguration(
      name, refName, criteria, PropertiesBundle.message("properties.files.inspection.group.display.name"), PropertiesFileType.INSTANCE);
  }

  @Override
  public @NotNull PsiElement @NotNull [] createPatternTree(@NotNull String text,
                                                           @NotNull PatternContextInfo contextInfo,
                                                           @NotNull LanguageFileType fileType,
                                                           @NotNull Language language,
                                                           @NotNull Project project,
                                                           boolean physical) {
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("__dummy.properties", language, text, physical, true);
    final PsiElement child = file.getFirstChild();
    if (child == null) return PsiElement.EMPTY_ARRAY;
    SmartList<PsiElement> result = new SmartList<>();
    PsiElement patternElement = child.getFirstChild();
    while (patternElement != null) {
      if (patternElement instanceof Property || patternElement instanceof PsiComment) {
        result.add(patternElement);
      }
      patternElement = patternElement.getNextSibling();
    }

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @Override
  public void compile(PsiElement @NotNull [] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    if (elements.length > 1) throw new MalformedPatternException();
    final CompiledPattern pattern = globalVisitor.getContext().getPattern();
    for (PsiElement element : elements) {
      if (element instanceof PsiComment) {
        final PsiComment comment = (PsiComment)element;
        final String commentText = comment.getText();
        if (globalVisitor.hasFragments(commentText)) {
          final MatchingHandler handler =
            globalVisitor.processPatternStringWithFragments(commentText, GlobalCompilingVisitor.OccurenceKind.COMMENT);
          if (handler != null) {
            comment.putUserData(CompiledPattern.HANDLER_KEY, handler);
          }
        }
        else {
          globalVisitor.handle(comment);
        }
        pattern.getHandler(element).setFilter(e -> e instanceof PsiComment);
      }
      else if (element instanceof Property) {
        final Property property = (Property)element;
        pattern.getHandler(element).setFilter(e -> e instanceof Property);
        final PsiElement firstChild = property.getFirstChild();
        if (firstChild instanceof PropertyKeyImpl) {
          globalVisitor.handle(firstChild);
          pattern.getHandler(firstChild).setFilter(e -> e instanceof PropertyKeyImpl);
        }
        final PsiElement lastChild = property.getLastChild();
        if (lastChild instanceof PropertyValueImpl) {
          final String valueText = lastChild.getText();
          final MatchingHandler handler =
            globalVisitor.processPatternStringWithFragments(valueText, GlobalCompilingVisitor.OccurenceKind.TEXT);
          if (handler != null) {
            lastChild.putUserData(CompiledPattern.HANDLER_KEY, handler);
          }
          pattern.getHandler(firstChild).setFilter(e -> e instanceof PropertyValueImpl);
        }
      }
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  @Override
  public @NotNull PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof Property) {
          Property other = globalVisitor.getElement(Property.class);
          final PsiElement key = element.getFirstChild();
          if (globalVisitor.setResult(globalVisitor.match(key, other.getFirstChild()))) {
            final PsiElement value = element.getLastChild();
            if (value instanceof PropertyValueImpl) {
              final PsiElement otherValue = other.getLastChild();
              globalVisitor.setResult(globalVisitor.matchOptionally(value, otherValue instanceof PropertyValueImpl ? otherValue : null));
            }
          }
        }
        else if (element instanceof PropertyKeyImpl) {
          final PropertyKeyImpl other = globalVisitor.getElement(PropertyKeyImpl.class);
          final MatchContext context = globalVisitor.getMatchContext();
          final MatchingHandler handler = context.getPattern().getHandler(element);
          if (handler instanceof SubstitutionHandler) {
            globalVisitor.setResult(((SubstitutionHandler)handler).handle(other, context));
          }
          else {
            globalVisitor.setResult(globalVisitor.matchText(element, other));
          }
        }
        else if (element instanceof PropertyValueImpl) {
          final PropertyValueImpl other = globalVisitor.getElement(PropertyValueImpl.class);
          final MatchContext context = globalVisitor.getMatchContext();
          final MatchingHandler handler = element.getUserData(CompiledPattern.HANDLER_KEY);
          if (handler instanceof LiteralWithSubstitutionHandler) {
            globalVisitor.setResult(handler.match(element, other, context));
          }
          else if (handler instanceof SubstitutionHandler) {
            globalVisitor.setResult(((SubstitutionHandler)handler).handle(other, context));
          }
          else {
            globalVisitor.setResult(globalVisitor.matchText(element, other));
          }
        }
      }

      @Override
      public void visitComment(@NotNull PsiComment comment) {
        final PsiElement other = globalVisitor.getElement();
        final MatchingHandler handler = comment.getUserData(CompiledPattern.HANDLER_KEY);
        globalVisitor.setResult(handler instanceof LiteralWithSubstitutionHandler
                                ? handler.match(comment, other, globalVisitor.getMatchContext())
                                : globalVisitor.matchText(comment, other));
      }
    };
  }

  @Override
  public @NotNull CompiledPattern createCompiledPattern() {
    return new CompiledPattern() {
      {
        setStrategy(PropertiesMatchingStrategy.INSTANCE);
      }

      @Override
      public String @NotNull [] getTypedVarPrefixes() {
        return new String[] {TYPED_VAR_PREFIX};
      }

      @Override
      public boolean isTypedVar(@NotNull String str) {
        return str.startsWith(TYPED_VAR_PREFIX);
      }
    };
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language == PropertiesLanguage.INSTANCE;
  }

  @Override
  public @NotNull Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return PropertiesContextType.class;
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return new DocumentBasedReplaceHandler(project);
  }
}
