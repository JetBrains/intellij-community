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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 24, 2005
 * Time: 9:54:02 PM
 */
package com.theoryinpractice.testng.model;

public enum TestType
{

    PACKAGE("PACKAGE", "All in package", 0),
    CLASS  ("CLASS", "Class", 1),
    METHOD ("METHOD", "Method", 2),
    GROUP  ("GROUP", "Group", 3),
    SUITE  ("SUITE", "Suite", 4),
    PATTERN("PATTERN", "Pattern", 5),
    SOURCE ("SOURCE", "Source location", 6);
    
    public final String type;
    private final String presentableName;
    public final int value;

    TestType(String type, String presentableName, int value) {
        this.type = type;
        this.presentableName = presentableName;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public int getValue() {
        return value;
    }

    public String getPresentableName() {
        return presentableName;
    }
}