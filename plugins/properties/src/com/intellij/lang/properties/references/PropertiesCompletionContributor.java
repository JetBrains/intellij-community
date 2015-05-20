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
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.EmptyResourceBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
public class PropertiesCompletionContributor extends CompletionContributor {
  public PropertiesCompletionContributor() {
    extend(null, psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        doAdd(parameters, result);
      }
    });
  }

  private static void doAdd(CompletionParameters parameters, final CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiReference[] references = ArrayUtil.mergeArrays(position.getReferences(), position.getParent().getReferences());
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

  public static boolean hasMoreImportantReference(@NotNull PsiReference[] references, @NotNull PropertyReference propertyReference) {
    return propertyReference.isSoft() && ContainerUtil.or(references, new Condition<PsiReference>() {
      @Override
      public boolean value(PsiReference reference) {
        return !reference.isSoft();
      }
    });
  }

  public static final LookupElementRenderer<LookupElement> LOOKUP_ELEMENT_RENDERER = new LookupElementRenderer<LookupElement>() {
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
        IProperty defaultProperty = defaultPropertiesFile.findPropertyByKey(key);
        if (defaultProperty != null) {
          value = defaultProperty.getValue();
        }
      }

      if (hasBundle) {
        presentation.setTypeText(resourceBundle.getBaseName(), AllIcons.FileTypes.Properties);
      }

      if (presentation instanceof RealLookupElementPresentation && value != null) {
        value = "=" + value;
        int limit = 1000;
        if (value.length() > limit || !((RealLookupElementPresentation)presentation).hasEnoughSpaceFor(value, false)) {
          if (value.length() > limit) {
            value = value.substring(0, limit);
          }
          while (value.length() > 0 && !((RealLookupElementPresentation)presentation).hasEnoughSpaceFor(value + "...", false)) {
            value = value.substring(0, value.length() - 1);
          }
          value += "...";
        }
      }

      TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_VALUE);
      presentation.setTailText(value, attrs.getForegroundColor());
    }
  };

  @NotNull
  public static LookupElement[] getVariants(final PropertyReferenceBase propertyReference) {
    final Set<Object> variants = PropertiesPsiCompletionUtil.getPropertiesKeys(propertyReference);
    return getVariants(variants);
  }

  public static LookupElement[] getVariants(Set<Object> variants) {
    List<LookupElement> elements = ContainerUtil.mapNotNull(variants, new NullableFunction<Object, LookupElement>() {
      @Override
      public LookupElement fun(Object o) {
        if (o instanceof String) return LookupElementBuilder.create((String)o).withIcon(PlatformIcons.PROPERTY_ICON);
        return createVariant((IProperty)o);
      }
    });
    return elements.toArray(new LookupElement[elements.size()]);
  }

  @Nullable
  public static LookupElement createVariant(IProperty property) {
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
