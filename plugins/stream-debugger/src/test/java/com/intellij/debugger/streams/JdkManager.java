/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
