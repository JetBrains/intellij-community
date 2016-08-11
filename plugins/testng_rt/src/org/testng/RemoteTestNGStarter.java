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
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class RemoteTestNGStarter {
  private static final String SOCKET = "-socket";

  public static void main(String[] args) throws Exception {
    int i = 0;
    String param = null;
    String commandFileName = null;
    String workingDirs = null;
    Vector resultArgs = new Vector();
    for (; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith("@name")) {
        param = arg.substring(5);
        continue;
      }
      else if (arg.startsWith("@w@")) {
        workingDirs = arg.substring(3);
        continue;
      }
      else if (arg.startsWith("@@@")) {
        commandFileName = arg.substring(3);
        continue;
      }
      else if (arg.startsWith(ForkedDebuggerHelper.DEBUG_SOCKET)) {
        continue;
      }
      else if (arg.startsWith(SOCKET)) {
        final int port = Integer.parseInt(arg.substring(SOCKET.length()));
        try {
          final Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);  //start collecting tests
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

    final List newArgs = new ArrayList();
    try {
      final String cantRunMessage = "CantRunException";
      while (true) {
        String line = reader.readLine();
        while (line == null) {
          line = reader.readLine();
        }

        if (line.startsWith(cantRunMessage) && !new File(line).exists()) {
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
        newArgs.add(line);
      }
    }
    finally {
      reader.close();
    }

    resultArgs.addAll(newArgs);

    if (commandFileName != null) {
      if (workingDirs != null && new File(workingDirs).length() > 0) {
        System.exit(new TestNGForkedSplitter(workingDirs, System.out, System.err, newArgs)
                      .startSplitting(args, param, commandFileName, null));
        return;
      }
    }
    final IDEARemoteTestNG testNG = new IDEARemoteTestNG(param);
    CommandLineArgs cla = new CommandLineArgs();
    new JCommander(Collections.singletonList(cla), (String[])resultArgs.toArray(new String[resultArgs.size()]));
    testNG.configure(cla);
    testNG.run();
  }
}
