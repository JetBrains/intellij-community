/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 01-Jul-2009
 */
package org.testng;

import com.beust.jcommander.JCommander;
import org.testng.remote.RemoteArgs;
import org.testng.remote.RemoteTestNG;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

public class RemoteTestNGStarter {
  public static boolean SM_RUNNER = System.getProperty("idea.testng.sm_runner") != null;
  private static final String SOCKET = "-socket";
  public static void main(String[] args) throws Exception {
    int i = 0;
    Vector resultArgs = new Vector();
    for (; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(SOCKET)) {
        final int port = Integer.parseInt(arg.substring(SOCKET.length()));
        try {
          final Socket socket = new Socket(InetAddress.getByName(null), port);  //start collecting tests
          final DataInputStream os = new DataInputStream(socket.getInputStream());
          try {
            os.readBoolean();//wait for ready flag
          }
          finally {
            os.close();
          }
        }
        catch (IOException e) {
          e.printStackTrace();
        }
        continue; //do not add socket to actual params
      }
      else if (arg.equals("-temp")) {
        break;
      }
      resultArgs.add(arg);
    }

    final File temp = new File(args[++i]);

    final BufferedReader reader = new BufferedReader(new FileReader(temp));

    try {
      final String cantRunMessage = "CantRunException";
      while (true) {
        String line = reader.readLine();
        while (line == null) {
          line = reader.readLine();
        }

        if (line.startsWith(cantRunMessage) && !new File(line).exists()){
          System.err.println(line.substring(cantRunMessage.length()));
          while (true) {
            line = reader.readLine();
            if (line == null || line.equals("end")) break;
            System.err.println(line);
          }
          System.exit(1);
          return;
        }
        if (line.equals("end")) break;
        resultArgs.add(line);
      }
    }
    finally {
      reader.close();
    }

    if (SM_RUNNER) {
      final IDEARemoteTestNG testNG = new IDEARemoteTestNG();
      CommandLineArgs cla = new CommandLineArgs();
      RemoteArgs ra = new RemoteArgs();
      new JCommander(Arrays.asList(cla, ra), (String[])resultArgs.toArray(new String[resultArgs.size()]));
      testNG.configure(cla);
      testNG.run();
      return;
    }

    try {
      //testng 5.10 do not initialize xml suites before run in normal main call => No test suite found.
      //revert "cleanup" to set suites manually again, this time for old versions only
      final Class aClass = Class.forName("org.testng.TestNGCommandLineArgs");
      final Method parseCommandLineMethod = aClass.getDeclaredMethod("parseCommandLine", new Class[] {new String[0].getClass()});
      final Map commandLineArgs = (Map)parseCommandLineMethod.invoke(null, new Object[] {(String[])resultArgs.toArray(new String[resultArgs.size()])});
      final RemoteTestNG testNG = new RemoteTestNG();
      testNG.configure(commandLineArgs);
      //set suites manually
      testNG.initializeSuitesAndJarFile();
      //in order to prevent suites to be initialized twice (second time in run)
      //clear string suites here
      testNG.setTestSuites(new ArrayList());
      testNG.run();
      return;
    }
    catch (Throwable ignore) {}

    RemoteTestNG.main((String[])resultArgs.toArray(new String[resultArgs.size()]));
  }
}
