package com.jetbrains.rest.run.sphinx;

import com.google.common.collect.Lists;
import com.intellij.ui.CollectionComboBoxModel;

import java.util.List;

/**
 * User : catherine
 */
public class SphinxTasksModel extends CollectionComboBoxModel {
  private static List<String> targets = Lists.newArrayList();
  static {
    targets.add("changes");
    targets.add("coverage");
    targets.add("devhelp");
    targets.add("dirhtml");
    targets.add("doctest");
    targets.add("epub");
    targets.add("gettext");
    targets.add("html");
    targets.add("htmlhelp");
    targets.add("json");
    targets.add("latex");
    targets.add("latexpdf");
    targets.add("linkcheck");
    targets.add("man");
    targets.add("pickle");
    targets.add("qthelp");
    targets.add("singlehtml");
    targets.add("text");
    targets.add("texinfo");
    targets.add("web");
    targets.add("websupport");
  }

  public SphinxTasksModel() {
    super(getTasks(), "html");
  }

  private static List<String> getTasks() {
    return targets;
  }
}
