package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;

import java.util.*;

public class PropertiesGrouper implements Grouper{
  public static final String ID = "SHOW_PROPERTIES";

  public Collection<Group> group(Collection<TreeElement> children) {
    Map<Group,Group> result = new HashMap<Group, Group>();
    for (Iterator<TreeElement> iterator = children.iterator(); iterator.hasNext();) {
      Object o = iterator.next();
      if (o instanceof JavaClassTreeElementBase){
        PsiElement element = ((JavaClassTreeElementBase)o).getElement();
        PropertyGroup group = PropertyGroup.createOn(element);
        if (group != null) {
          PropertyGroup existing = (PropertyGroup)result.get(group);
          if (existing != null){
            existing.copyAccessorsFrom(group);
          } else {
            result.put(group, group);
          }
        }
      }
    }
    return result.values();
  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData("Show Properties", null, IconLoader.getIcon("/nodes/property.png"));
  }

  public String getName() {
    return ID;
  }
}
