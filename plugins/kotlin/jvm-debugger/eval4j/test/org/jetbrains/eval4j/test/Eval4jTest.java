// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.eval4j.test;

import junit.framework.TestSuite;
import org.jetbrains.eval4j.jdi.test.JdiTestKt;

public class Eval4jTest extends TestSuite {

    @SuppressWarnings({"UnnecessaryFullyQualifiedName", "StaticMethodReferencedViaSubclass"})
    public static TestSuite suite() {
        TestSuite eval4jSuite = new TestSuite("Eval4j Tests");
        eval4jSuite.addTest(JdiTestKt.suite());
        eval4jSuite.addTest(MainKt.suite());
        return eval4jSuite;
    }
}
