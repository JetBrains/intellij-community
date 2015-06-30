/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.testng;

import com.beust.jcommander.JCommander;
import com.intellij.rt.execution.testFrameworks.ChildVMStarter;
import org.testng.remote.RemoteArgs;

import java.io.PrintStream;
import java.util.Arrays;

public class TestNGForkedStarter extends ChildVMStarter {
  public static void main(String[] args) throws Exception {
    new TestNGForkedStarter().startVM(args);
  }

  @Override
  protected void configureFrameworkAndRun(String[] args, PrintStream out, PrintStream err) throws Exception {
    final IDEARemoteTestNG testNG = new IDEARemoteTestNG(null);
    CommandLineArgs cla = new CommandLineArgs();
    RemoteArgs ra = new RemoteArgs();
    String[] resultArgs = new String[args.length - 1];
    System.arraycopy(args, 1, resultArgs, 0, resultArgs.length);
    new JCommander(Arrays.asList(cla, ra), resultArgs);
    testNG.configure(cla);
    testNG.run();
    System.exit(0);
  }
}
