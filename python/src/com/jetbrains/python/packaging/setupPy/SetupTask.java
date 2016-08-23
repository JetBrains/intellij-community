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
package com.jetbrains.python.packaging.setupPy;

import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.gotoByName.ChooseByNameItem;

import java.util.ArrayList;
import java.util.List;

/**
* @author yole
*/
public class SetupTask implements ChooseByNameItem {
  private final String name;
  private String description;
  private final List<Option> options = new ArrayList<>();

  SetupTask(String name) {
    this.name = name;
    description = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public List<Option> getOptions() {
    return ImmutableList.copyOf(options);
  }

  public void addOption(Option option) {
    options.add(option);
  }

  public static class Option {
    public final String name;
    public final String description;
    public final boolean checkbox;
    public final boolean negative;

    public Option(String name, String description, boolean checkbox, boolean negative) {
      this.name = name;
      this.description = description;
      this.checkbox = checkbox;
      this.negative = negative;
    }
  }
}
