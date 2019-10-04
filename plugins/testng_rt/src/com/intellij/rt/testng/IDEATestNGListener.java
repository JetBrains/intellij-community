// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.rt.testng;

import org.testng.ITestListener;

public interface IDEATestNGListener extends ITestListener {
  String EP_NAME = "com.theoryinpractice.testng.listener";
}