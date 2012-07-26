package com.jetbrains.rest.run.sphinx;

import com.google.common.collect.Lists;
import com.intellij.ui.CollectionComboBoxModel;

import java.util.List;

/**
 * User : catherine
 */
public class SphinxTasksModel extends CollectionComboBoxModel {
  public SphinxTasksModel() {
    super(getTasks(), "html");
  }

  private static List<String> getTasks() {
    List<String> result = Lists.newArrayList();
    result.add("html");
    result.add("dirhtml");
    result.add("singlehtml");
    result.add("pickle");
    result.add("json");
    result.add("web");
    result.add("htmlhelp");
    result.add("devhelp");
    result.add("qthelp");
    result.add("epub");
    result.add("latex");
    result.add("text");
    result.add("man");
    result.add("texinfo");
    result.add("changes");
    result.add("linkcheck");
    result.add("websupport");
    result.add("gettext");
    result.add("doctest");
    result.add("coverage");
    return result;
  }
}
