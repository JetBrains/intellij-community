// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.eval4j.jdi.test;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class Debugee {
    public static void main(String[] args) {
        // BREAKPOINT
        Runtime.getRuntime();
        System.out.println("hi");
    }
}
