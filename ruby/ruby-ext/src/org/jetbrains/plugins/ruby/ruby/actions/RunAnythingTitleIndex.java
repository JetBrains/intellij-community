package org.jetbrains.plugins.ruby.ruby.actions;

class RunAnythingTitleIndex {
  volatile int recentUndefined = -1;
  volatile int temporaryRunConfigurations = -1;
  volatile int permanentRunConfigurations = -1;
  volatile int generators = -1;
  volatile int rakeTasks = -1;
  volatile int bundler = -1;

  final String gotoPermanentRunConfigurationsTitle;
  final String gotoRakeTitle;
  final String gotoBundlerTitle;
  final String gotoRecentUndefinedTitle;
  final String gotoTemporaryRunConfigurationsTitle;
  final String gotoGeneratorsTitle;

  RunAnythingTitleIndex() {
    //todo move to Bundle
    gotoPermanentRunConfigurationsTitle = "Permanent configurations";
    gotoRakeTitle = "Rake tasks";
    gotoBundlerTitle = "Bundler actions";
    gotoRecentUndefinedTitle = "Recent commands";
    gotoTemporaryRunConfigurationsTitle = "Temporary Configurations";
    gotoGeneratorsTitle = "Generators";
  }

  String getTitle(int index) {
    if (index == recentUndefined) return gotoRecentUndefinedTitle;
    if (index == generators) return gotoGeneratorsTitle;
    if (index == temporaryRunConfigurations) return gotoTemporaryRunConfigurationsTitle;
    if (index == permanentRunConfigurations) return gotoPermanentRunConfigurationsTitle;
    if (index == rakeTasks) return gotoRakeTitle;
    if (index == bundler) return gotoBundlerTitle;

    return null;
  }

  public void clear() {
    temporaryRunConfigurations = -1;
    recentUndefined = -1;
    permanentRunConfigurations = -1;
    rakeTasks = -1;
    generators = -1;
    bundler = -1;
  }

  public void shift(int index, int shift) {
    if (bundler != -1 && bundler > index) bundler += shift;
    if (rakeTasks != -1 && rakeTasks > index) rakeTasks += shift;
    if (generators != -1 && generators > index) generators += shift;
    if (permanentRunConfigurations != -1 && permanentRunConfigurations > index) permanentRunConfigurations += shift;
    if (temporaryRunConfigurations != -1 && temporaryRunConfigurations > index) temporaryRunConfigurations += shift;
  }
}
