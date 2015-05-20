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
package com.intellij.codeInsight.template.emmet.filters;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;

/**
 * Bem filter for emmet support.
 * See the original source code here: https://github.com/emmetio/emmet/blob/master/javascript/filters/bem.js
 * And documentation here: http://docs.emmet.io/filters/bem/
 */
public class BemEmmetFilter extends ZenCodingFilter {
  public static final String SUFFIX = "bem";

  private static final Key<BemState> BEM_STATE = Key.create("BEM_STATE");

  private static final String ELEMENT_SEPARATOR = "__";
  private static final String MODIFIER_SEPARATOR = "_";
  private static final String SHORT_ELEMENT_PREFIX = "-";

  private static final Joiner ELEMENTS_JOINER = Joiner.on(ELEMENT_SEPARATOR).skipNulls();
  private static final Splitter ELEMENTS_SPLITTER = Splitter.on(ELEMENT_SEPARATOR);
  private static final Splitter MODIFIERS_SPLITTER = Splitter.on(MODIFIER_SEPARATOR).limit(2);
  private static final Splitter CLASS_NAME_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private static final Joiner CLASS_NAME_JOINER = Joiner.on(' ');

  private static final Function<String, String> CLASS_NAME_NORMALIZER = new Function<String, String>() {
    @NotNull
    @Override
    public String apply(@NotNull String input) {
      if (!input.startsWith(SHORT_ELEMENT_PREFIX)) {
        return input;
      }

      StringBuilder result = new StringBuilder();
      while (input.startsWith(SHORT_ELEMENT_PREFIX)) {
        input = input.substring(SHORT_ELEMENT_PREFIX.length());
        result.append(ELEMENT_SEPARATOR);
      }
      return result.append(input).toString();
    }
  };

  private static final Predicate<String> BLOCK_NAME_PREDICATE = new Predicate<String>() {
    @Override
    public boolean apply(String className) {
      return Pattern.compile("^[A-z]-").matcher(className).matches();
    }
  };
  private static final Predicate<String> STARTS_WITH_LETTER = new Predicate<String>() {
    @Override
    public boolean apply(@Nullable String input) {
      return input != null && input.length() > 0 && Character.isLetter(input.charAt(0));
    }
  };

  @NotNull
  @Override
  public String getDisplayName() {
    return "BEM";
  }

  @NotNull
  @Override
  public String getSuffix() {
    return SUFFIX;
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @NotNull
  @Override
  public GenerationNode filterNode(@NotNull final GenerationNode node) {
    final Map<String, String> attributes = node.getTemplateToken().getAttributes();
    String classValue = attributes.get(HtmlUtil.CLASS_ATTRIBUTE_NAME);
    if (classValue != null) {
      Iterable<String> classNames = extractClasses(classValue);
      BEM_STATE.set(node, new BemState(suggestBlockName(classNames), null, null));
      final Set<String> newClassNames = ImmutableSet.copyOf(concat(transform(classNames, new Function<String, Iterable<String>>() {
        @Override
        public Iterable<String> apply(String className) {
          return processClassName(className, node);
        }
      })));
      attributes.put(HtmlUtil.CLASS_ATTRIBUTE_NAME, CLASS_NAME_JOINER.join(newClassNames));
    }
    return node;
  }

  private static Iterable<String> processClassName(String className, @NotNull GenerationNode node) {
    className = fillWithBemElements(className, node);
    className = fillWithBemModifiers(className, node);

    BemState nodeBemState = BEM_STATE.get(node);
    BemState bemState = extractBemStateFromClassName(className);
    List<String> result = newLinkedList();
    if (!bemState.isEmpty()) {
      String block = bemState.getBlock();
      if (isNullOrEmpty(block)) {
        block = nullToEmpty(nodeBemState != null ? nodeBemState.getBlock() : null);
        bemState.setBlock(block);
      }
      String prefix = block;
      String element = bemState.getElement();
      if (!isNullOrEmpty(element)) {
        prefix += ELEMENT_SEPARATOR + element;
      }
      result.add(prefix);
      String modifier = bemState.getModifier();
      if (!isNullOrEmpty(modifier)) {
        result.add(prefix + MODIFIER_SEPARATOR + modifier);
      }
      BEM_STATE.set(node, bemState.copy());
    }
    else {
      result.add(className);
    }
    return result;
  }

  @NotNull
  private static BemState extractBemStateFromClassName(@NotNull String className) {
    final BemState result = new BemState();
    if (className.contains(ELEMENT_SEPARATOR)) {
      final Iterator<String> elementsIterator = ELEMENTS_SPLITTER.split(className).iterator();
      result.setBlock(elementsIterator.next());

      final Collection<String> elementParts = newLinkedList();
      while (elementsIterator.hasNext()) {
        final String elementPart = elementsIterator.next();
        if (!elementsIterator.hasNext()) {
          final List<String> elementModifiers = newArrayList(MODIFIERS_SPLITTER.split(elementPart));
          elementParts.add(getFirst(elementModifiers, null));
          if (elementModifiers.size() > 1) {
            result.setModifier(getLast(elementModifiers, ""));
          }
        }
        else {
          elementParts.add(elementPart);
        }
      }
      result.setElement(ELEMENTS_JOINER.join(elementParts));
    }
    else if (className.contains(MODIFIER_SEPARATOR)) {
      final Iterable<String> blockModifiers = MODIFIERS_SPLITTER.split(className);
      result.setBlock(getFirst(blockModifiers, ""));
      result.setModifier(getLast(blockModifiers, ""));
    }
    return result;
  }

  @NotNull
  private static String fillWithBemElements(@NotNull String className, @NotNull GenerationNode node) {
    return transformClassNameToBemFormat(className, ELEMENT_SEPARATOR, node);
  }

  @NotNull
  private static String fillWithBemModifiers(@NotNull String className, @NotNull GenerationNode node) {
    return transformClassNameToBemFormat(className, MODIFIER_SEPARATOR, node);
  }

  /**
   * Adduction className to BEM format according to tags structure.
   *
   * @param className
   * @param separator handling separator
   * @param node      current node
   * @return class name in BEM format
   */
  @NotNull
  private static String transformClassNameToBemFormat(@NotNull String className, @NotNull String separator, @NotNull GenerationNode node) {
    Pair<String, Integer> cleanStringAndDepth = getCleanStringAndDepth(className, separator);
    Integer depth = cleanStringAndDepth.second;
    if (depth > 0) {
      GenerationNode donor = node;
      while (donor.getParent() != null && depth > 0) {
        donor = donor.getParent();
        depth--;
      }

      BemState bemState = BEM_STATE.get(donor);
      if (bemState != null) {
        String prefix = bemState.getBlock();
        if (!isNullOrEmpty(prefix)) {
          String element = bemState.getElement();
          if (MODIFIER_SEPARATOR.equals(separator) && !isNullOrEmpty(element)) {
            prefix = prefix + separator + element;
          }
          return prefix + separator + cleanStringAndDepth.first;
        }
      }
    }
    return className;
  }

  /**
   * Counts separators at the start of className and retrieve className without these separators.
   *
   * @param name
   * @param separator
   * @return pair like <name_without_separator_at_the_start, count_of_separators_at_the_start_of_string>
   */
  @NotNull
  private static Pair<String, Integer> getCleanStringAndDepth(@NotNull String name, @NotNull String separator) {
    int result = 0;
    while (name.startsWith(separator)) {
      result++;
      name = name.substring(separator.length());
    }
    return Pair.create(name, result);
  }

  /**
   * Extract all normalized class names from class attribute value
   *
   * @param classAttributeValue
   * @return collection of normalized class names
   */
  @NotNull
  private static Iterable<String> extractClasses(String classAttributeValue) {
    return transform(CLASS_NAME_SPLITTER.split(classAttributeValue), CLASS_NAME_NORMALIZER);
  }

  /**
   * Suggest block name by class names.
   * Returns first class started with pattern [a-z]-
   * or first class started with letter.
   *
   * @param classNames
   * @return suggested block name for given classes. Empty string if name can't be suggested.
   */
  @NotNull
  private static String suggestBlockName(Iterable<String> classNames) {
    return find(classNames, BLOCK_NAME_PREDICATE, find(classNames, STARTS_WITH_LETTER, ""));
  }

  private static class BemState {
    @Nullable private String block;
    @Nullable private String element;
    @Nullable private String modifier;

    private BemState() {
    }

    private BemState(@Nullable String block, @Nullable String element, @Nullable String modifier) {
      this.block = block;
      this.element = element;
      this.modifier = modifier;
    }

    public void setModifier(@Nullable String modifier) {
      this.modifier = modifier;
    }

    public void setElement(@Nullable String element) {
      this.element = element;
    }

    public void setBlock(@Nullable String block) {
      this.block = block;
    }

    @Nullable
    public String getBlock() {
      return block;
    }

    @Nullable
    public String getElement() {
      return element;
    }

    @Nullable
    public String getModifier() {
      return modifier;
    }

    public boolean isEmpty() {
      return isNullOrEmpty(block) && isNullOrEmpty(element) && isNullOrEmpty(modifier);
    }

    @Nullable
    public BemState copy() {
      return new BemState(block, element, modifier);
    }
  }
}
