// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.testng.TestNGForkedSplitter;
import com.intellij.util.ArrayUtilRt;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
      final String modulePathFromFile = "MODULE_PATH";
      List<String> moduleExpectedOptions = Arrays.asList("-p", modulePathFromFile);
      FileUtil.writeToFile(tempFile, "p\n" +
                                     workingDirFromFile + "\n" +
                                     "mod1\n" +
                                     classpathFromFile + "\n" +
                                     modulePathFromFile + "\n" +
                                     "0\n" +
                                     "1\n" +
                                     "p.T1\n");
      final File commandLineFile = FileUtil.createTempFile(tempDirectory, "commandline", null);
      final String dynamicClasspath = "dynamic classpath";
      final String[] vmParams = new String[] {"vm executable", "param1", "param2"};
      FileUtil.writeToFile(commandLineFile, dynamicClasspath + "\n" + StringUtil.join(vmParams, "\n") + "\n");
      new TestNGForkedSplitter(tempFile.getCanonicalPath(), Collections.singletonList(tempFile.getCanonicalPath())) {
        private boolean myStarted = false;
        @Override
        protected int startChildFork(List<String> args, File workingDir, String classpath, List<String> moduleOptions, String repeatCount) throws IOException {
          Assert.assertEquals(dynamicClasspath, myDynamicClasspath);
          Assert.assertArrayEquals(vmParams, myVMParameters.toArray());
          Assert.assertEquals(workingDirFromFile, workingDir.getName());
          Assert.assertEquals(classpathFromFile, classpath);
          Assert.assertEquals(moduleExpectedOptions, moduleOptions);
          Assert.assertEquals(1, args.size());
          final String generatedSuite = FileUtil.loadFile(new File(args.get(0)));
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
      }.startSplitting(ArrayUtilRt.EMPTY_STRING_ARRAY, "", commandLineFile.getCanonicalPath(), null);
    }
    finally {
      FileUtil.delete(tempDirectory);
    }
  }
}
