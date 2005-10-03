package com.intellij.rt.execution.junit2;

import com.intellij.rt.execution.junit.TextTestRunner2;
import com.intellij.rt.execution.junit2.segments.SegmentedOutputStream;
import junit.textui.TestRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Vector;

/**
 * Before rename or move
 *  @see com.intellij.execution.junit.JUnitConfiguration.JUNIT_START_CLASS
 *  @noinspection HardCodedStringLiteral
 */
public class JUnitStarter {
  public static int VERSION = 5;
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
    System.exit(prepareStreamsAndStart(removeVersionParameter(args), out, err));
  }

  private static String[] removeVersionParameter(String[] args) {
    Vector vector = new Vector();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(IDE_VERSION))
        continue;
      vector.addElement(arg);
    }
    String[] result = new String[vector.size()];
    for (int i = 0; i < vector.size(); i++)
      result[i] = (String) vector.elementAt(i);
    return result;
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
    JUnitStarter.getAgentClass();
    new TestRunner().setPrinter(new IdeaJUnitAgent.MockResultPrinter());
  }

  public static int prepareStreamsAndStart(String[] args, SegmentedOutputStream out, SegmentedOutputStream err) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    int result;
    try {
      String encoding = System.getProperty("file.encoding");
      System.setOut(new PrintStream(out));
      System.setErr(new PrintStream(err));
      SegmentedStreamsUser testRunner = (SegmentedStreamsUser) getAgentClass().newInstance();
      testRunner.setStreams(out, err);
      result = TextTestRunner2.startRunnerWithArgs((TextTestRunner2) testRunner, args);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      result = -2;
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return result;
  }

  static Class getAgentClass() throws ClassNotFoundException {
    return Class.forName("com.intellij.rt.execution.junit2.IdeaJUnitAgent");
  }
}
