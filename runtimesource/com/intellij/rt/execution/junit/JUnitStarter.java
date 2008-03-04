package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
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
  public static final String JUNIT4_PARAMETER = "-junit4";

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
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      argList.addElement(arg);
    }
    boolean isJUnit4 = removeVersionParameters(argList);
    String[] array = new String[argList.size()];
    argList.copyInto(array);
    int exitCode = prepareStreamsAndStart(array, isJUnit4, out, err);
    System.exit(exitCode);
  }

  private static boolean removeVersionParameters(Vector args) {
    boolean isJunit4 = false;
    Vector result = new Vector(args.size());
    for (int i = 0; i < args.size(); i++) {
      String arg = (String)args.get(i);
      if (arg.startsWith(IDE_VERSION)) {
        //ignore
      }
      else if (arg.equals(JUNIT4_PARAMETER)){
        isJunit4 = true;
      }
      else {
        result.addElement(arg);
      }
    }
    args.removeAllElements();
    for (int i = 0; i < result.size(); i++) {
      String arg = (String)result.get(i);
      args.addElement(arg);
    }
    return isJunit4;
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
    new TestRunner().setPrinter(new IdeaTestRunner.MockResultPrinter());
  }

  private static int prepareStreamsAndStart(String[] args, final boolean isJUnit4, SegmentedOutputStream out, SegmentedOutputStream err) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    int result;
    try {
      System.setOut(new PrintStream(out));
      System.setErr(new PrintStream(err));
      IdeaTestRunner testRunner = (IdeaTestRunner)getAgentClass().newInstance();
      if (isJUnit4) {
        testRunner.JUNIT4_API = (JUnit4API)Class.forName("com.intellij.rt.junit4.JUnit4Util").newInstance();
      }
      testRunner.setStreams(out, err);
      result = IdeaTestRunner.startRunnerWithArgs(testRunner, args);
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
    return Class.forName("com.intellij.rt.execution.junit.IdeaTestRunner");
  }
}
