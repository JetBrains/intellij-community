package com.intellij.debugger.streams;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
public class JdkManager {
  public static final String JDK18_PATH;

  private static final String MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-";

  private static class Holder {
    static final Sdk JDK18 = ((JavaSdkImpl)JavaSdk.getInstance()).createMockJdk("java 1.8", JDK18_PATH, false);
  }

  static {
    JDK18_PATH = new File("java/" + MOCK_JDK_DIR_NAME_PREFIX + "1.8").getAbsolutePath();
  }

  public static Sdk getMockJdk18() {
    return Holder.JDK18;
  }
}
