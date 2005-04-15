package com.intellij.lang.properties.structureView;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashMap;

import java.util.*;

/**
 * @author cdr
 */
public class GroupByWordPrefixes implements Grouper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.structureView.GroupByWordPrefixes");
  private static final String ID = "GROUP_BY_PREFIXES";

  public Collection<Group> group(Collection<TreeElement> children) {
    List<List<String>> keys = new ArrayList<List<String>>();
    String parentPrefix="";
    int parentPrefixLength=0;
    //if (parentNode.getValue() instanceof PropertiesPrefixGroup) {
    //  parentPrefix = ((PropertiesPrefixGroup)parentNode.getValue()).getPrefix();
    //  parentPrefixLength = parentPrefix.split("\\.").length;
    //}
    //else {
    //  parentPrefix = "";
    //  parentPrefixLength = 0;
    //}

    for (Iterator<TreeElement> iterator = children.iterator(); iterator.hasNext();) {
      TreeElement element = iterator.next();
      if (element instanceof PropertiesStructureViewElement) {
        Property property = ((PropertiesStructureViewElement)element).getValue();
        String key = property.getKey();
        if (key == null) continue;
        LOG.assertTrue(key.startsWith(parentPrefix));
        List<String> words = Arrays.asList(key.split("\\."));
        keys.add(words);
      }
    }
    Collections.sort(keys, new Comparator<List<String>>() {
      public int compare(final List<String> o1, final List<String> o2) {
        if (o1.size() != o2.size()) return o1.size() - o2.size();
        for (int i = 0; i < o1.size(); i++) {
          String s1 = o1.get(i);
          String s2 = o2.get(i);
          int res = s1.compareTo(s2);
          if (res != 0) return res;
        }
        return 0;
      }
    });
    List<Group> groups = new ArrayList<Group>();
    int groupStart = 0;
    for (int i = 0; i <= keys.size(); i++) {
      List<String> key = i == keys.size() ? null : keys.get(i);
      if (i == keys.size() || (i > 0 && !Comparing.strEqual(key.get(parentPrefixLength), keys.get(i - 1).get(parentPrefixLength)))) {
        // find longest group prefix
        List<String> firstKey = keys.get(groupStart);
        int prefixLen = firstKey.size();
        for (int j = groupStart+1; j < i; j++) {
          List<String> prevKey = keys.get(j-1);
          List<String> nextKey = keys.get(j);
          for (int k = parentPrefixLength; k < prefixLen; k++) {
            String word = k < nextKey.size() ? nextKey.get(k) : null;
            String wordInPrevKey = k < prevKey.size() ? prevKey.get(k) : null;
            if (!Comparing.strEqual(word, wordInPrevKey)) {
              prefixLen = k;
              break;
            }
          }
        }
        String prefix = StringUtil.concatenate(firstKey.subList(0,prefixLen).toArray(new String[prefixLen]), ".");
        String presentableName = prefix.substring(parentPrefix.length());
        groups.add(new PropertiesPrefixGroup(prefix,presentableName));
        groupStart = i;
      }
    }
    return groups;
  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData("Group By Prefixes",
                                      "Groups properties by common key prefixes separated by dots",
                                      IconLoader.getIcon("/nodes/addLocalWeblogicInstance.png"));
  }

  public String getName() {
    return ID;
  }

  private static class PrefixTree {
    final String word;
    final Map<String,PrefixTree> children = new THashMap<String, PrefixTree>();

    public PrefixTree(final String word) {
      this.word = word;
    }

    void insertProperty(List<String> words, int wordIndex) {
      if (wordIndex == words.size()) return;
      String word = words.get(wordIndex);
      PrefixTree child = children.get(word);
      if (child == null) {
        child = new PrefixTree(word);
        children.put(word, child);
      }
      child.insertProperty(words, wordIndex+1);
    }
  }

}
