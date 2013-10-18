package com.jetbrains.rest.run.docutils;

import com.google.common.collect.Lists;
import com.intellij.ui.CollectionComboBoxModel;

import java.util.List;

/**
 * User : catherine
 */
public class DocutilsTasksModel extends CollectionComboBoxModel {
  public DocutilsTasksModel() {
    super(getTasks(), "rst2html");
  }

  private static List<String> getTasks() {
    List<String> result = Lists.newArrayList();
    result.add("rst2html");
    result.add("rst2latex");
    result.add("rst2odt");
    result.add("rst2pseudoxml");
    result.add("rst2s5");
    result.add("rst2xml");
    result.add("rstpep2html");
    return result;
  }
}
