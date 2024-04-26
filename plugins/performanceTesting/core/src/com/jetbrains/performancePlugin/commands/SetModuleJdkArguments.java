package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class SetModuleJdkArguments {
  @Argument
  String moduleName;

  @Argument
  String jdkName;

  @Argument
  String jdkType;

  @Argument
  String jdkPath;
}
