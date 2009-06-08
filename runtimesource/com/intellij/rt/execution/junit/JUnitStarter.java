package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import com.intellij.rt.junit3.JUnit3IdeaTestRunner;
import junit.textui.TestRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Vector;

/**
 * Before rename or move
 *  @see com.intellij.execution.junit.JUnitConfiguration#JUNIT_START_CLASS
 *  @noinspection HardCodedStringLiteral
 */
public class JUnitStarter {
  public static final int VERSION = 5;
  public static final String IDE_VERSION = "-ideVersion";

  public static void main(String[] args) throws IOException {
    SegmentedOutputStream out = new SegmentedOutputStream(System.out);
    SegmentedOutputStream err = new SegmentedOutputStream(System.err);
    if (!canWorkWithJUnitVersion(err)) {
      err.flush();
      System.exit(-3);
    }
    if (!checkVersion(args, err)) {
      err.flush();
      System.exit(-3);
    }

    Vector argList = new Vector();
    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      argList.addElement(arg);
    }
    String[] array = new String[argList.size()];
    argList.copyInto(array);
    int exitCode = prepareStreamsAndStart(array, out, err);
    System.exit(exitCode);
  }



  public static boolean checkVersion(String[] args, SegmentedOutputStream notifications) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(IDE_VERSION)) {
        int ideVersion = Integer.parseInt(arg.substring(IDE_VERSION.length(), arg.length()));
        if (ideVersion != VERSION) {
          PrintStream stream = new PrintStream(notifications);
          stream.println("Wrong agent version: " + VERSION + ". IDE expects version: " + ideVersion);
          stream.flush();
          return false;
        } else
          return true;
      }
    }
    return false;
  }

  private static boolean canWorkWithJUnitVersion(OutputStream notifications) {
    final PrintStream stream = new PrintStream(notifications);
    try {
      junitVersionChecks();
    } catch (Throwable e) {
      stream.println("!!! JUnit version 3.8 or later expected:");
      stream.println();
      e.printStackTrace(stream);
      stream.flush();
      return false;
    } finally {
      stream.flush();
    }
    return true;
  }

  private static void junitVersionChecks() throws ClassNotFoundException {
    Class.forName("junit.framework.ComparisonFailure");
    getAgentClass();
    new TestRunner().setPrinter(new JUnit3IdeaTestRunner.MockResultPrinter());
  }

  private static int prepareStreamsAndStart(String[] args, SegmentedOutputStream out, SegmentedOutputStream err) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    int result;
    try {
      System.setOut(new PrintStream(out));
      System.setErr(new PrintStream(err));
      IdeaTestRunner testRunner = (IdeaTestRunner)getAgentClass().newInstance();
      testRunner.setStreams(out, err);
      result = testRunner.startRunnerWithArgs(args);
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      result = -2;
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return result;
  }

  private static Class getAgentClass() throws ClassNotFoundException {
    try {
      Class.forName("org.junit.runner.JUnitCore");
      return Class.forName("com.intellij.rt.junit4.JUnit4IdeaTestRunner");
    }
    catch (ClassNotFoundException e) {
      return Class.forName("com.intellij.rt.junit3.JUnit3IdeaTestRunner");
    }
  }
}
