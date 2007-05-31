package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.impl.java.AccessLevelProvider;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.SourceComparator;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Comparator;

public class VisibilityComparator implements Comparator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.structureView.impl.VisibilityComparator");
  private static final int GROUP_ACCESS_SUBLEVEL = 1;
  public static Comparator THEN_SOURCE = new VisibilityComparator(SourceComparator.INSTANCE);
  public static Comparator THEN_ALPHA = new VisibilityComparator(AlphaComparator.INSTANCE);
  public static Comparator IMSTANCE = new VisibilityComparator(null);

  private Comparator myNextComparator;
  private static final int UNKNOWN_ACCESS_LEVEL = -1;

  public VisibilityComparator(Comparator comparator) {
    myNextComparator = comparator;
  }

  public int compare(Object descriptor1, Object descriptor2) {
    int accessLevel1 = getAccessLevel(descriptor1);
    int accessLevel2 = getAccessLevel(descriptor2);
    if (accessLevel1 == accessLevel2 && myNextComparator != null) {
      return myNextComparator.compare(descriptor1, descriptor2);
    }
    return accessLevel2 - accessLevel1;
  }

  private static int getAccessLevel(Object element) {
    if (element instanceof AccessLevelProvider) {
      return ((AccessLevelProvider)element).getAccessLevel() * (GROUP_ACCESS_SUBLEVEL + 1) + ((AccessLevelProvider)element).getSubLevel();
    }
    else {
      LOG.error(element.getClass().getName());
      return UNKNOWN_ACCESS_LEVEL;
    }    
  }
}
