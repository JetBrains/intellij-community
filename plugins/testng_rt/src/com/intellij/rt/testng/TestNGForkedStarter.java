// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import com.beust.jcommander.JCommander;
import org.testng.CommandLineArgs;

import java.util.Collections;

public final class TestNGForkedStarter  {
  public static void main(String[] args) {
    final IDEARemoteTestNG testNG = new IDEARemoteTestNG(null);
    CommandLineArgs cla = new CommandLineArgs();
    new JCommander(Collections.singletonList(cla)).parse(args);
    testNG.configure(cla);
    testNG.run();
    System.exit(0);
  }
}
