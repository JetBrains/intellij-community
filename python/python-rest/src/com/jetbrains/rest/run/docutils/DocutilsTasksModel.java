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
