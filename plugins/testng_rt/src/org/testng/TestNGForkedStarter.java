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
import com.intellij.rt.execution.junit.ForkedStarter;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import org.testng.remote.RemoteArgs;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class TestNGForkedStarter extends ForkedStarter {
  public static void main(String[] args) throws Exception {
    System.out.println("started");
    new TestNGForkedStarter().startVM(args);
  }

  @Override
  protected String getStarterName() {
    return TestNGForkedStarter.class.getName();
  }

  int startForkedVMs(String workingDirsPath,
                     String[] args,
                     String configName,
                     Object out,
                     Object err,
                     String commandLinePath) throws Exception {
    return startForkedVM(workingDirsPath, args, configName, out, err, "none", commandLinePath, Collections.emptyList());
  }

  @Override
  protected List createChildArgsForClasses(List newArgs, String packageName, String workingDir, List classNames, Object rootDescriptor)
    throws IOException {
    final LinkedHashMap<String, Map<String, List<String>>> classes = new LinkedHashMap<String, Map<String, List<String>>>();
    for (Object className : classNames) {
      classes.put((String)className, null);
    }
    final File file =
      TestNGXmlSuiteHelper.writeSuite(classes, new LinkedHashMap<String, String>(), "testName", null,
                                      TestNGXmlSuiteHelper.Logger.DEAF);
    file.deleteOnExit();
    
    final List childArgs = new ArrayList();
    childArgs.add(file.getAbsolutePath());
    
    return childArgs;
  }

  @Override
  protected void startVM(String[] args, PrintStream out, PrintStream err)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
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

  //--------------------- fork under class level -------------------------------------

  @Override
  protected List getChildren(Object child) {
    return Collections.emptyList();
  }

  @Override
  protected List createChildArgs(List args, Object child) {
    return Collections.emptyList();
  }

  @Override
  protected Object findByClassName(String className, Object rootDescription) {
    return rootDescription;
  }

  @Override
  protected String getTestClassName(Object child) {
    return null;
  }

  @Override
  protected Object createRootDescription(String[] args, List newArgs, String configName, Object out, Object err)
    throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    return new Object();
  }

}
