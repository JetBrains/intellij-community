package org.jetbrains.plugins.ruby.ruby.actions;

class RunAnythingMoreIndex {
  volatile int permanentRunConfigurations = -1;
  volatile int rakeTasks = -1;
  volatile int bundlerActions = -1;
  volatile int temporaryRunConfigurations = -1;
  volatile int generators = -1;
  volatile int undefined = -1;

  public void shift(int index, int shift) {
    if (temporaryRunConfigurations >= index) temporaryRunConfigurations += shift;
    if (permanentRunConfigurations >= index) permanentRunConfigurations += shift;
    if (rakeTasks >= index) rakeTasks += shift;
    if (bundlerActions >= index) bundlerActions += shift;
    if (generators >= index) generators += shift;
    if (undefined >= index) undefined += shift;
  }
}
