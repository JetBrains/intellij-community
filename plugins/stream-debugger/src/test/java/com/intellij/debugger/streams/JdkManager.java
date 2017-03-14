package com.intellij.debugger.streams;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
class JdkManager {
  private static final String MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-";
  private static final Sdk JDK18;

  static {
    final String path = new File("java/" + MOCK_JDK_DIR_NAME_PREFIX + "1.8").getAbsolutePath();
    JDK18 = ((JavaSdkImpl)JavaSdk.getInstance()).createMockJdk("java 1.8", path, false);
  }

  static Sdk getMockJdk18() {
    return JDK18;
  }
}
