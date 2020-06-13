// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import com.beust.jcommander.JCommander;
import org.testng.CommandLineArgs;
import org.testng.remote.RemoteArgs;

import java.util.Arrays;

public class TestNGForkedStarter  {
  public static void main(String[] args) {
    final IDEARemoteTestNG testNG = new IDEARemoteTestNG(null);
    CommandLineArgs cla = new CommandLineArgs();
    RemoteArgs ra = new RemoteArgs();
    new JCommander(Arrays.asList(cla, ra), args);
    testNG.configure(cla);
    testNG.run();
    System.exit(0);
  }
}
