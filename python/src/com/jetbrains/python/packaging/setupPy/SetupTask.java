// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.setupPy;

import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.gotoByName.ChooseByNameItem;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author yole
*/
public class SetupTask implements ChooseByNameItem {
  private final @NlsSafe String name;
  private @NlsSafe String description;
  private final List<Option> options = new ArrayList<>();

  SetupTask(@NotNull @NlsSafe String name) {
    this.name = name;
    description = name;
  }

  @Override
  public @NlsSafe String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @NotNull
  @Override
  public @NlsSafe String getName() {
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
    public final @NlsSafe String description;
    public final boolean checkbox;
    public final boolean negative;

    public Option(String name, @NlsSafe String description, boolean checkbox, boolean negative) {
      this.name = name;
      this.description = description;
      this.checkbox = checkbox;
      this.negative = negative;
    }
  }
}
