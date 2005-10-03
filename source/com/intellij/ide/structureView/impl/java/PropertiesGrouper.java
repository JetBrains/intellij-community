package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.IdeBundle;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import gnu.trove.THashMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Collections;

import org.jetbrains.annotations.NonNls;

public class PropertiesGrouper implements Grouper{
  @NonNls public static final String ID = "SHOW_PROPERTIES";

  public Collection<Group> group(final AbstractTreeNode parent, Collection<TreeElement> children) {
    if (parent.getValue() instanceof PropertyGroup) return Collections.EMPTY_LIST;
    Map<Group,Group> result = new THashMap<Group, Group>();
    for (TreeElement o : children) {
      if (o instanceof JavaClassTreeElementBase) {
        PsiElement element = ((JavaClassTreeElementBase)o).getElement();
        PropertyGroup group = PropertyGroup.createOn(element, o);
        if (group != null) {
          PropertyGroup existing = (PropertyGroup)result.get(group);
          if (existing != null) {
            existing.copyAccessorsFrom(group);
          }
          else {
            result.put(group, group);
          }
        }
      }
    }
    for (Iterator<Group> iterator = result.keySet().iterator(); iterator.hasNext();) {
      PropertyGroup group = (PropertyGroup)iterator.next();
      if (!group.isComplete()) {
        iterator.remove();
      }
    }
    return result.values();
  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.properties"), null, Icons.PROPERTY_ICON);
  }

  public String getName() {
    return ID;
  }
}
