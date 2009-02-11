package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.StructureViewModel;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:13:23 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PropertiesGroupingStructureViewModel extends StructureViewModel {
  void setSeparator(String separator);

  String getSeparator();
}
