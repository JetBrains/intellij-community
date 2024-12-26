// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.editor.PropertyStructureViewElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class GroupByWordPrefixes implements Grouper, Sorter {
  private static final Logger LOG = Logger.getInstance(GroupByWordPrefixes.class);
  public static final @NonNls String ID = "GROUP_BY_PREFIXES";
  private String mySeparator;

  public GroupByWordPrefixes(String separator) {
    mySeparator = separator;
  }

  public void setSeparator(final String separator) {
    mySeparator = separator;
  }

  public String getSeparator() {
    return mySeparator;
  }

  @Override
  public @NotNull @Unmodifiable Collection<Group> group(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<TreeElement> children) {
    List<Key> keys = new ArrayList<>();

    String parentPrefix;
    int parentPrefixLength;
    if (parent.getValue() instanceof PropertiesPrefixGroup) {
      parentPrefix = ((PropertiesPrefixGroup)parent.getValue()).getPrefix();
      parentPrefixLength = StringUtil.split(parentPrefix, mySeparator).size();
    }
    else {
      parentPrefix = "";
      parentPrefixLength = 0;
    }
    for (TreeElement element : children) {
      final String text = getPropertyUnescapedKey(element);
      if (text == null) continue;
      boolean expected = text.startsWith(parentPrefix) || text.startsWith(mySeparator);
      if (!expected) LOG.error("unexpected text: " + text + "; parentPrefix=" + parentPrefix + "; mySeparator=" + mySeparator);
      List<String> words = StringUtil.split(text, mySeparator);
      keys.add(new Key(words, element));
    }

    if (keys.isEmpty()) return ContainerUtil.emptyList();

    keys.sort((k1, k2) -> {
      List<String> o1 = k1.words;
      List<String> o2 = k2.words;
      for (int i = 0; i < Math.max(o1.size(), o2.size()); i++) {
        if (i == o1.size()) return 1;
        if (i == o2.size()) return -1;
        String s1 = o1.get(i);
        String s2 = o2.get(i);
        int res = s1.compareTo(s2);
        if (res != 0) return res;
      }
      return 0;
    });
    List<Group> groups = new ArrayList<>();
    int groupStart = 0;
    for (int i = 0; i <= keys.size(); i++) {
      if (!isEndOfGroup(i, keys, parentPrefixLength)) {
        continue;
      }
      // find longest group prefix
      List<String> firstKey = groupStart == keys.size() ? Collections.emptyList() : keys.get(groupStart).words;
      List<TreeElement> groupChildren = new SmartList<>();
      groupChildren.add(keys.get(groupStart).node);
      int prefixLen = firstKey.size();
      for (int j = groupStart+1; j < i; j++) {
        List<String> prevKey = keys.get(j-1).words;
        List<String> nextKey = keys.get(j).words;
        for (int k = parentPrefixLength; k < prefixLen; k++) {
          String word = k < nextKey.size() ? nextKey.get(k) : null;
          String wordInPrevKey = k < prevKey.size() ? prevKey.get(k) : null;
          if (!Comparing.strEqual(word, wordInPrevKey)) {
            prefixLen = k;
            break;
          }
        }
        groupChildren.add(keys.get(j).node);
      }
      String[] strings = firstKey.subList(0,prefixLen).toArray(new String[prefixLen]);
      String prefix = StringUtil.join(strings, mySeparator);
      String presentableName = prefix.substring(parentPrefix.length());
      presentableName = StringUtil.trimStart(presentableName, mySeparator);
      if (i - groupStart > 1) {
        groups.add(new PropertiesPrefixGroup(groupChildren, prefix, presentableName, mySeparator));
      }
      else if (groupStart != keys.size()) {
        TreeElement node = keys.get(groupStart).node;
        ((PropertyStructureViewElement)node).setPresentableName(presentableName);
      }
      groupStart = i;
    }
    return groups;
  }

  private static boolean isEndOfGroup(final int i,
                                      final List<Key> keys,
                                      final int parentPrefixLength) {
    if (i == keys.size()) return true;
    if (i == 0) return false;
    List<String> words = keys.get(i).words;
    List<String> prevWords = keys.get(i - 1).words;
    if (prevWords.size() == parentPrefixLength) return true;
    if (words.size() == parentPrefixLength) return true;
    return !Comparing.strEqual(words.get(parentPrefixLength), prevWords.get(parentPrefixLength));
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(PropertiesBundle.message("structure.view.group.by.prefixes.action.name"),
                                      PropertiesBundle.message("structure.view.group.by.prefixes.action.description"),
                                      AllIcons.Actions.GroupByPrefix);
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public @NotNull Comparator getComparator() {
    return Sorter.ALPHA_SORTER.getComparator();
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  private record Key(List<String> words, TreeElement node) {
  }

  static @Nullable String getPropertyUnescapedKey(@NotNull TreeElement element) {
    if (!(element instanceof StructureViewTreeElement)) {
      return null;
    }
    Object value = ((StructureViewTreeElement)element).getValue();
    if (!(value instanceof IProperty)) {
      return null;
    }
    return ((IProperty) value).getUnescapedKey();
  }
}
