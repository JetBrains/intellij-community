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
package com.theoryinpractice.testng.configuration;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.junit.Assert;
import org.junit.Test;
import org.testng.TestNGForkedSplitter;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class TestNGForkTest {
  @Test
  public void testForkPerModule() throws Exception {
    final File tempDirectory = FileUtil.createTempDirectory("testng_fork", null);
    try {
      final File tempFile = FileUtil.createTempFile(tempDirectory, "workingDir", null);
      final String workingDirFromFile = "MODULE_1";
      final String classpathFromFile = "CLASSPATH";
      FileUtil.writeToFile(tempFile, "p\n" +
                                     workingDirFromFile + "\n" +
                                     "mod1\n" +
                                     classpathFromFile + "\n" +
                                     "1\n" +
                                     "p.T1\n");
      final File commandLineFile = FileUtil.createTempFile(tempDirectory, "commandline", null);
      final String dynamicClasspath = "dynamic classpath";
      final String[] vmParams = new String[] {"vm executable", "param1", "param2"};
      FileUtil.writeToFile(commandLineFile, dynamicClasspath + "\n" + StringUtil.join(vmParams, "\n") + "\n");
      new TestNGForkedSplitter(tempFile.getCanonicalPath(), Collections.singletonList(tempFile.getCanonicalPath())) {
        private boolean myStarted = false;
        @Override
        protected int startChildFork(List args, File workingDir, String classpath, String repeatCount) throws IOException {
          Assert.assertEquals(dynamicClasspath, myDynamicClasspath);
          Assert.assertArrayEquals(vmParams, myVMParameters.toArray());
          Assert.assertEquals(workingDirFromFile, workingDir.getName());
          Assert.assertEquals(classpathFromFile, classpath);
          Assert.assertTrue(args.size() == 1);
          final String generatedSuite = FileUtil.loadFile(new File((String)args.get(0)));
          Assert.assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                              "<!DOCTYPE suite SYSTEM \"http://testng.org/testng-1.0.dtd\">\n" +
                              "<suite name=\"Default Suite\">\n" +
                              "  <test name=\"mod1\">\n" +
                              "    <classes>\n" +
                              "      <class name=\"p.T1\"/>\n" +
                              "    </classes>\n" +
                              "  </test> <!-- mod1 -->\n" +
                              "</suite> <!-- Default Suite -->\n", StringUtil.convertLineSeparators(generatedSuite));
          Assert.assertFalse(myStarted);
          myStarted = true;
          return 0;
        }
      }.startSplitting(ArrayUtil.EMPTY_STRING_ARRAY, "", commandLineFile.getCanonicalPath(), null);
    }
    finally {
      FileUtil.delete(tempDirectory);
    }
  }
}
