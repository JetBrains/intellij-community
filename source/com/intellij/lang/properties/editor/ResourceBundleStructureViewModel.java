package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:13:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class ResourceBundleStructureViewModel implements StructureViewModel {
  private final ResourceBundle myResourceBundle;

  public ResourceBundleStructureViewModel(ResourceBundle root) {
    myResourceBundle = root;
  }

  public StructureViewTreeElement getRoot() {
    return new ResourceBundleFileStructureViewElement(myResourceBundle);
  }

  public Grouper[] getGroupers() {
    return new Grouper[]{new GroupByWordPrefixes()};
  }

  public Sorter[] getSorters() {
    return new Sorter[] {Sorter.ALPHA_SORTER};
  }

  public Filter[] getFilters() {
    return new Filter[0];
  }

  public Object getCurrentEditorElement() {
    return null;
  }

  public void addEditorPositionListener(FileEditorPositionListener listener) {

  }

  public void removeEditorPositionListener(FileEditorPositionListener listener) {

  }

  public void addModelListener(ModelListener modelListener) {

  }

  public void removeModelListener(ModelListener modelListener) {

  }

  public void dispose() {

  }
}
