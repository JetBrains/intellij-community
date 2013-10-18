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
  private final List<Option> options = new ArrayList<Option>();

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
