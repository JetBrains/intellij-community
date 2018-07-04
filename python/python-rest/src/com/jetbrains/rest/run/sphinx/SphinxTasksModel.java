/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.rest.run.sphinx;

import com.google.common.collect.Lists;
import com.intellij.ui.CollectionComboBoxModel;

import java.util.List;

/**
 * User : catherine
 */
public class SphinxTasksModel extends CollectionComboBoxModel<String> {
  private static final List<String> targets = Lists.newArrayList();
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
