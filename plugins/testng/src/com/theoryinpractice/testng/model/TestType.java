/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.theoryinpractice.testng.model;

import com.intellij.openapi.util.NlsContexts;
import com.theoryinpractice.testng.TestngBundle;

import java.util.function.Supplier;

public enum TestType
{

    PACKAGE("PACKAGE", () -> TestngBundle.message("label.all.in.package.test.type"), 0),
    CLASS  ("CLASS", () -> TestngBundle.message("label.class.test.type"), 1),
    METHOD ("METHOD", () -> TestngBundle.message("label.method.test.type"), 2),
    GROUP  ("GROUP", () -> TestngBundle.message("label.group.test.type"), 3),
    SUITE  ("SUITE", () -> TestngBundle.message("label.suite.test.type"), 4),
    PATTERN("PATTERN", () -> TestngBundle.message("label.pattern.test.type"), 5),
    SOURCE ("SOURCE", () -> TestngBundle.message("label.source.location.test.type"), 6);
    
    public final String type;
    private final Supplier<@NlsContexts.Label String> presentableNameSupplier;
    public final int value;

    TestType(String type, Supplier<@NlsContexts.Label String> presentableNameSupplier, int value) {
        this.type = type;
        this.presentableNameSupplier = presentableNameSupplier;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public int getValue() {
        return value;
    }

    public @NlsContexts.Label String getPresentableName() {
        return presentableNameSupplier.get();
    }
}