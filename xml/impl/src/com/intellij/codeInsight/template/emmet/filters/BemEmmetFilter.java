/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bem filter for emmet support.
 * See the original source code here: https://github.com/emmetio/emmet/blob/master/javascript/filters/bem.js
 * And documentation here: http://docs.emmet.io/filters/bem/
 */
public class BemEmmetFilter extends ZenCodingFilter {
  private static final String SUFFIX = "bem";
  private static final Key<BemState> BEM_STATE = Key.create("BEM_STATE");
  private static final Pattern BLOCK_NAME_PATTERN = Pattern.compile("^[A-z]-");

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
    String classAttributeName = getClassAttributeName();
    String classValue = attributes.get(classAttributeName);
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    if (classValue != null && emmetOptions != null) {
      String elementSeparator = emmetOptions.getBemElementSeparator();
      String modifierSeparator = emmetOptions.getBemModifierSeparator();
      String shortElementPrefix = emmetOptions.getBemShortElementPrefix();

      List<String> classNames = ContainerUtil.map(HtmlUtil.splitClassNames(classValue), (s) -> normalizeClassName(s, elementSeparator, shortElementPrefix));

      BEM_STATE.set(node, new BemState(suggestBlockName(classNames), null, null));
      Set<String> newClassNames = ContainerUtil.newLinkedHashSet();
      for (String className : classNames) {
        ContainerUtil.addAll(newClassNames, processClassName(className, node, elementSeparator, modifierSeparator));
      }
      attributes.put(classAttributeName, StringUtil.join(newClassNames, " "));
    }
    return node;
  }

  @NotNull
  public String getClassAttributeName() {
    return HtmlUtil.CLASS_ATTRIBUTE_NAME;
  }

  private static Iterable<String> processClassName(@NotNull String className, @NotNull GenerationNode node,
                                                   @NotNull String elementSeparator, @NotNull String modifierSeparator) {
    className = fillWithBemElements(className, node, elementSeparator);
    className = fillWithBemModifiers(className, node, modifierSeparator);

    BemState nodeBemState = BEM_STATE.get(node);
    BemState bemState = extractBemStateFromClassName(className, elementSeparator, modifierSeparator);
    List<String> result = ContainerUtil.newArrayList();
    if (!bemState.isEmpty()) {
      String nodeBlockValue = nodeBemState != null ? nodeBemState.getBlock() : null;

      String block = bemState.getBlock();
      if (StringUtil.isEmpty(block)) {
        block = StringUtil.notNullize(nodeBlockValue);
        bemState.setBlock(block);
      }
      String prefix = block;
      String element = bemState.getElement();
      if (StringUtil.isNotEmpty(element)) {
        prefix += elementSeparator + element;
      }
      result.add(prefix);
      String modifier = bemState.getModifier();
      if (StringUtil.isNotEmpty(modifier)) {
        result.add(prefix + modifierSeparator + modifier);
      }

      BemState newNodeBemState = bemState.copy();
      if (StringUtil.isNotEmpty(nodeBlockValue) && StringUtil.isEmpty(modifier)) {
        // save old value 
        newNodeBemState.setBlock(nodeBlockValue);
      }
      BEM_STATE.set(node, newNodeBemState);
    }
    else {
      result.add(className);
    }
    return result;
  }

  @NotNull
  private static BemState extractBemStateFromClassName(@NotNull String className, String elementSeparator, String modifierSeparator) {
    final BemState result = new BemState();
    int indexOfElementSeparator = className.indexOf(elementSeparator);
    if (indexOfElementSeparator >= 0) {
      result.setBlock(className.substring(0, indexOfElementSeparator));
      result.setElement(className.substring(indexOfElementSeparator + elementSeparator.length()));

      int lastIndexOfElementSeparator = className.lastIndexOf(elementSeparator);
      assert lastIndexOfElementSeparator >= 0;
      int indexOfModifierSeparator = className.indexOf(modifierSeparator, lastIndexOfElementSeparator + elementSeparator.length());
      if (indexOfModifierSeparator >= 0) {
        result.setModifier(className.substring(indexOfModifierSeparator + modifierSeparator.length()));
        result.setElement(className.substring(indexOfElementSeparator + elementSeparator.length(), indexOfModifierSeparator));
      }
    }
    else {
      int indexOfModifierSeparator = className.indexOf(modifierSeparator);
      if (indexOfModifierSeparator >= 0) {
        result.setBlock(className.substring(0, indexOfModifierSeparator));
        result.setModifier(className.substring(indexOfModifierSeparator + modifierSeparator.length()));
      }
    }
    return result;
  }

  @NotNull
  private static String fillWithBemElements(@NotNull String className, @NotNull GenerationNode node, @NotNull String separator) {
    return transformClassNameToBemFormat(className, separator, node, false);
  }

  @NotNull
  private static String fillWithBemModifiers(@NotNull String className, @NotNull GenerationNode node, @NotNull String separator) {
    return transformClassNameToBemFormat(className, separator, node, true);
  }


  /**
   * Adjusting className to BEM format according to tags structure.
   *
   * @param className
   * @param separator           handling separator
   * @param node                current node
   * @param isModifierSeparator whether we're trying to handle modifier
   * @return class name in BEM format
   */
  @NotNull
  private static String transformClassNameToBemFormat(@NotNull String className, @NotNull String separator,
                                                      @NotNull GenerationNode node, boolean isModifierSeparator) {
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
        if (!StringUtil.isEmpty(prefix)) {
          String element = bemState.getElement();
          if (isModifierSeparator && !StringUtil.isEmpty(element)) {
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
    while (!separator.isEmpty() && name.startsWith(separator)) {
      result++;
      name = name.substring(separator.length());
    }
    return Pair.create(name, result);
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
    String result = ContainerUtil.find(classNames, className -> BLOCK_NAME_PATTERN.matcher(className).matches());
    if (result == null) {
      result = ContainerUtil.find(classNames, s -> s != null && !s.isEmpty() && Character.isLetter(s.charAt(0)));
    }
    return StringUtil.notNullize(result);
  }

  @NotNull
  private static String normalizeClassName(@NotNull String className,
                                           @NotNull String elementSeparator,
                                           @NotNull String shortElementPrefix) {
    if (shortElementPrefix.isEmpty() || !className.startsWith(shortElementPrefix)) {
      return className;
    }

    StringBuilder result = new StringBuilder();
    while (className.startsWith(shortElementPrefix)) {
      className = className.substring(shortElementPrefix.length());
      result.append(elementSeparator);
    }
    return result.append(className).toString();
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
      return StringUtil.isEmpty(block) && StringUtil.isEmpty(element) && StringUtil.isEmpty(modifier);
    }

    @NotNull
    public BemState copy() {
      return new BemState(block, element, modifier);
    }
  }
}
