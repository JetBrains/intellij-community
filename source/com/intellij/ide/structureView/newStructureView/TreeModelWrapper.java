package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;

import java.util.ArrayList;

public class TreeModelWrapper implements StructureViewModel {
  private final StructureViewModel myModel;
  private final TreeActionsOwner myStructureView;

  public TreeModelWrapper(StructureViewModel model, TreeActionsOwner structureView) {
    myModel = model;
    myStructureView = structureView;
  }

  public StructureViewTreeElement getRoot() {
    return myModel.getRoot();
  }

  public Grouper[] getGroupers() {
    ArrayList<TreeAction> filtered = filter(myModel.getGroupers());
    return filtered.toArray(new Grouper[filtered.size()]);
  }

  private ArrayList<TreeAction> filter(Object[] actions) {
    ArrayList<TreeAction> filtered = new ArrayList<TreeAction>();
    for (int i = 0; i < actions.length; i++) {
      TreeAction grouper = (TreeAction)actions[i];
      if (myStructureView.isActionActive(grouper.getName())) filtered.add(grouper);
    }
    return filtered;
  }

  public Sorter[] getSorters() {
    ArrayList<TreeAction> filtered = filter(myModel.getSorters());
    return filtered.toArray(new Sorter[filtered.size()]);
  }

  public Filter[] getFilters() {
    ArrayList<TreeAction> filtered = filter(myModel.getFilters());
    return filtered.toArray(new Filter[filtered.size()]);
  }

  public Object getCurrentEditorElement() {
    return myModel.getCurrentEditorElement();
  }
}
