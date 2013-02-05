/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Lists.newLinkedList;

/**
 * User: zolotov
 * Date: 2/4/13
 * <p/>
 * Bem filter for emmet support.
 * See the original source code here: https://github.com/emmetio/emmet/blob/master/javascript/filters/bem.js
 * And documentation here: http://docs.emmet.io/filters/bem/
 */
public class BemEmmetFilter extends ZenCodingFilter {

  private static final Key<String> BEM_BLOCK = Key.create("BEM_BLOCK");
  private static final Key<String> BEM_ELEMENT = Key.create("BEM_ELEMENT");
  private static final Key<String> BEM_MODIFIER = Key.create("BEM_MODIFIER");

  private static final String ELEMENT_SEPARATOR = "__";
  private static final Splitter ELEMENTS_SPLITTER = Splitter.on(ELEMENT_SEPARATOR);
  private static final String MODIFIER_SEPARATOR = "_";
  private static final Splitter MODIFIERS_SPLITTER = Splitter.on(MODIFIER_SEPARATOR).limit(2);
  private static final String SHORT_ELEMENT_PREFIX = "-";
  private static final Function<String, String> CLASS_NAME_NORMALIZER = new Function<String, String>() {
    @Override
    public String apply(String input) {
      return input.replaceAll(Pattern.quote(SHORT_ELEMENT_PREFIX), ELEMENT_SEPARATOR);
    }
  };

  private static final Splitter CLASS_NAME_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private static final Joiner CLASS_NAME_JOINER = Joiner.on(' ');
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
  public String getSuffix() {
    return "bem";
  }

  @NotNull
  @Override
  public GenerationNode filterNode(@NotNull final GenerationNode node) {
    final List<Pair<String, String>> attribute2Value = node.getTemplateToken().getAttribute2Value();
    Pair<String, String> classNamePair = getClassPair(attribute2Value);
    if (classNamePair != null) {
      Iterable<String> classNames = extractClasses(classNamePair.second);
      final String defaultBlockName = suggestBlockName(classNames);
      node.putUserData(BEM_BLOCK, defaultBlockName);
      final Set<String> newClassNames = ImmutableSet.copyOf(concat(transform(classNames, new Function<String, Iterable<String>>() {
        @Override
        public Iterable<String> apply(String className) {
          className = fillWithBemElements(className, node);
          className = fillWithBemModifiers(className, node);

          String block = null, element = null, modifier = null;

          List<String> result = newLinkedList();
          if (className.contains(ELEMENT_SEPARATOR)) {
            List<String> blockElements = newLinkedList(ELEMENTS_SPLITTER.split(className));
            block = getFirst(blockElements, "");
            if (blockElements.size() > 1) {
              List<String> elementModifiers = newLinkedList(MODIFIERS_SPLITTER.split(blockElements.get(1)));
              element = getFirst(elementModifiers, "");
              if (elementModifiers.size() > 1) {
                modifier = getLast(elementModifiers, "");
              }
            }
          }
          else if (className.contains(MODIFIER_SEPARATOR)) {
            Iterable<String> blockModifiers = MODIFIERS_SPLITTER.split(className);
            block = getFirst(blockModifiers, "");
            modifier = getLast(blockModifiers, "");
          }
          if (block != null || element != null || modifier != null) {
            if (isNullOrEmpty(block)) {
              block = nullToEmpty(node.getUserData(BEM_BLOCK));
            }
            String prefix = block;
            if (!isNullOrEmpty(element)) {
              prefix += ELEMENT_SEPARATOR + element;
            }
            result.add(prefix);

            if (!isNullOrEmpty(modifier)){
              result.add(prefix + MODIFIER_SEPARATOR + modifier);
            }

            node.putUserData(BEM_BLOCK, block);
            node.putUserData(BEM_ELEMENT, element);
            node.putUserData(BEM_MODIFIER, modifier);
          }
          else {
            result.add(className);
          }
          return result;
        }
      })));
      attribute2Value.add(Pair.create("class", CLASS_NAME_JOINER.join(newClassNames)));
    }
    return node;
  }

  private static String fillWithBemElements(String className, GenerationNode node) {
    return transformClassNameToBemFormat(className, ELEMENT_SEPARATOR, node);
  }

  private static String fillWithBemModifiers(String className, GenerationNode node) {
    return transformClassNameToBemFormat(className, MODIFIER_SEPARATOR, node);
  }

  private static String transformClassNameToBemFormat(String className, String separator, GenerationNode node) {
    Pair<String, Integer> cleanStringAndDepth = getCleanStringAndDepth(className, separator);
    Integer depth = cleanStringAndDepth.second;
    if (depth > 0) {
      GenerationNode donor = node;
      while (donor.getParent() != null && depth > 0) {
        donor = donor.getParent();
        depth--;
      }

      String prefix = donor.getUserData(BEM_BLOCK);
      if (!isNullOrEmpty(prefix)) {
        String element = donor.getUserData(BEM_ELEMENT);
        if (MODIFIER_SEPARATOR.equals(separator) && !isNullOrEmpty(element)) {
          prefix = prefix + separator + element;
        }
        return prefix + separator + cleanStringAndDepth.first;
      }
    }
    return className;
  }

  private static Pair<String, Integer> getCleanStringAndDepth(String name, String separator) {
    int result = 0;
    while (name.startsWith(separator)) {
      result++;
      name = name.substring(separator.length());
    }
    return Pair.create(name, result);
  }

  private static Iterable<String> extractClasses(String classAttributeValue) {
    return transform(CLASS_NAME_SPLITTER.split(classAttributeValue), CLASS_NAME_NORMALIZER);
  }

  private static String suggestBlockName(Iterable<String> classNames) {
    return find(classNames, BLOCK_NAME_PREDICATE, find(classNames, STARTS_WITH_LETTER, ""));
  }

  @Nullable
  private static Pair<String, String> getClassPair(List<Pair<String, String>> attribute2Value) {
    for (int i = 0; i < attribute2Value.size(); i++) {
      Pair<String, String> pair = attribute2Value.get(i);
      if ("class".equals(pair.first) && !isNullOrEmpty(pair.second)) {
        return attribute2Value.remove(i);
      }
    }
    return null;
  }

  @Override
  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return false; //todo: add setting for enabling this filter by default
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }
}
