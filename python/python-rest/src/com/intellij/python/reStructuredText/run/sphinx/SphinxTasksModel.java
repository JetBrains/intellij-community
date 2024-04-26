// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run.sphinx;

import com.intellij.ui.CollectionComboBoxModel;

import java.util.ArrayList;
import java.util.List;

/**
 * User : catherine
 */
public class SphinxTasksModel extends CollectionComboBoxModel<String> {
  private static final List<String> targets = new ArrayList<>();

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
