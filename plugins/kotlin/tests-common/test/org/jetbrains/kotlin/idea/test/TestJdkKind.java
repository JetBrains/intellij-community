// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

public enum TestJdkKind {
    MOCK_JDK,
    // JDK found at $JDK_16
    FULL_JDK_9,
    // JDK found at $JDK_15
    FULL_JDK_15,
    // JDK found at java.home
    FULL_JDK
}
