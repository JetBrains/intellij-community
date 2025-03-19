// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.unit;

import java.util.List;

public class JavaListWrapper {
    public List<Number> numbers;

    public JavaListWrapper(List<Number> numbers) {
        this.numbers = numbers;
    }
}
