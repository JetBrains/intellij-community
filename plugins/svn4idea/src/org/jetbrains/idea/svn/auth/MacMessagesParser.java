/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.auth;

import java.io.*;

/**
 * to parse http://developer.apple.com/library/mac/#documentation/Security/Reference/keychainservices/Reference/reference.html
 * User: Irina.Chernushina
 * Date: 9/12/12
 * Time: 4:14 PM
 */
public class MacMessagesParser {
  private final static String ourMapName = "macMessages";

  public static void main(String[] args) {
    final String file = args[0];
    final String outFile = args[1];

    final File file1 = new File(file);
    final File file2 = new File(outFile);
    if (! file1.exists()) {
      System.out.println("no file");
      return;
    }
    FileInputStream fis = null;
    FileOutputStream fos = null;
    try {
      fis = new FileInputStream(file1);
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));
      fos = new FileOutputStream(file2);
      final PrintStream printStream = new PrintStream(fos);

      int cnt = 0;
      while (true) {
        final String line = bufferedReader.readLine();
        if (line == null) break;
        processLine(line, printStream, cnt);
        ++ cnt;
        if (cnt >= 3) cnt = 0;
      }

      printStream.flush();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static void processLine(String line, PrintStream printStream, int cnt) {
    switch (cnt) {
      case 0:
        final String[] parts = line.trim().replace('\t', ' ').split(" ");
        if (parts.length != 2) throw new IllegalStateException(line);
        printStream.println();
        printStream.append(ourMapName).append(".put(").append(parts[1]).append(", new Trinity<String, String, String>(\"").
          append(parts[0]).append("\", \"");
        break;
      case 1:
        // description
        printStream.append(line.trim()).append("\", \"");
        break;
      case 2:
        //since
        printStream.append(line.trim()).append("\"));");
        break;
      default:
        throw new IllegalStateException();
    }
  }

    /*private final static Map<Integer, Trinity<String, String, String>> ourMap = new HashMap<Integer, Trinity<String, String, String>>();
    ourMap.put(123, new Trinity<String, String, String>(h,h,n));*/
}
