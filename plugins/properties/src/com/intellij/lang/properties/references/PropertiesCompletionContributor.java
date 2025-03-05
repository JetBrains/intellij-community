// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.EmptyResourceBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class PropertiesCompletionContributor extends CompletionContributor {
  public PropertiesCompletionContributor() {
    extend(null, psiElement(), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        doAdd(parameters, result);
      }
    });
  }

  private static void doAdd(CompletionParameters parameters, final CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    PsiElement gParent = parent != null ? parent.getParent() : null;
    PsiReference[] references = parent == null ? position.getReferences() : ArrayUtil.mergeArrays(position.getReferences(), parent.getReferences());
    if (gParent instanceof PsiLanguageInjectionHost && references.length == 0) {
      //kotlin
      PsiReference[] gParentReferences = gParent.getReferences();
      if (gParentReferences.length > 0) {
        references = ArrayUtil.mergeArrays(references, gParentReferences);
      }
    }
    PropertyReference propertyReference = ContainerUtil.findInstance(references, PropertyReference.class);
    if (propertyReference != null && !hasMoreImportantReference(references, propertyReference)) {
      final int startOffset = parameters.getOffset();
      PsiElement element = propertyReference.getElement();
      final int offsetInElement = startOffset - element.getTextRange().getStartOffset();
      TextRange range = propertyReference.getRangeInElement();
      if (offsetInElement >= range.getStartOffset()) {
        final String prefix = element.getText().substring(range.getStartOffset(), offsetInElement);

        LookupElement[] variants = getVariants(propertyReference);
        result.withPrefixMatcher(prefix).addAllElements(Arrays.asList(variants));
      }
    }
  }

  public static boolean hasMoreImportantReference(PsiReference @NotNull [] references, @NotNull PropertyReference propertyReference) {
    return propertyReference.isSoft() && ContainerUtil.or(references, reference -> !reference.isSoft());
  }

  public static final LookupElementRenderer<LookupElement> LOOKUP_ELEMENT_RENDERER = new LookupElementRenderer<>() {
    @Override
    public void renderElement(LookupElement element, LookupElementPresentation presentation) {
      IProperty property = (IProperty)element.getObject();
      presentation.setIcon(PlatformIcons.PROPERTY_ICON);
      String key = StringUtil.notNullize(property.getUnescapedKey());
      presentation.setItemText(key);

      PropertiesFile propertiesFile = property.getPropertiesFile();
      ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
      String value = property.getValue();
      boolean hasBundle = resourceBundle != EmptyResourceBundle.getInstance();
      if (hasBundle) {
        PropertiesFile defaultPropertiesFile = resourceBundle.getDefaultPropertiesFile();
        if (defaultPropertiesFile.getContainingFile() != propertiesFile.getContainingFile()) {
          IProperty defaultProperty = defaultPropertiesFile.findPropertyByKey(key);
          if (defaultProperty != null) {
            value = defaultProperty.getValue();
          }
        }
      }

      if (hasBundle) {
        presentation.setTypeText(resourceBundle.getBaseName(), AllIcons.FileTypes.Properties);
      }

      TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme()
        .getAttributes(PropertiesHighlighter.PropertiesComponent.PROPERTY_VALUE.getTextAttributesKey());
      presentation.setTailText("=" + value, attrs.getForegroundColor());
    }
  };

  public static LookupElement @NotNull [] getVariants(final PropertyReferenceBase propertyReference) {
    final Set<Object> variants = PropertiesPsiCompletionUtil.getPropertiesKeys(propertyReference);
    return getVariants(variants);
  }

  public static LookupElement[] getVariants(Set<Object> variants) {
    return variants.stream().map(o -> o instanceof String
           ? LookupElementBuilder.create((String)o).withIcon(PlatformIcons.PROPERTY_ICON)
           : createVariant((IProperty)o))
      .filter(Objects::nonNull).toArray(LookupElement[]::new);
  }

  public static @Nullable LookupElement createVariant(IProperty property) {
    String key = property.getKey();
    return key == null ? null : LookupElementBuilder.create(property, key).withRenderer(LOOKUP_ELEMENT_RENDERER);
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    if (context.getFile() instanceof PropertiesFile) {
      context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
    }
  }
}
