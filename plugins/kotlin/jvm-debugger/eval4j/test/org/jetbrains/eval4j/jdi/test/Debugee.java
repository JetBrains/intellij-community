// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.eval4j.jdi.test;

public class Debugee {
    public static void main(String[] args) {
        // BREAKPOINT
        Runtime.getRuntime();
        System.out.println("hi");
    }
}
