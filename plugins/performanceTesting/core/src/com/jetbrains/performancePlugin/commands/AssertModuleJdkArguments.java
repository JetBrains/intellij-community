package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class AssertModuleJdkArguments {
  @Argument
  String moduleName;

  @Argument
  String jdkVersion;

  @Argument
  AssertModuleJdkVersionCommand.Mode mode;
}
