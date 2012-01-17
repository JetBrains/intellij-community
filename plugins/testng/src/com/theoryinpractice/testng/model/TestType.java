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

public class TestType
{
    public static final TestType INVALID = new TestType("INVALID", -1);
    public static final TestType PACKAGE = new TestType("PACKAGE", 0);
    public static final TestType CLASS = new TestType("CLASS", 1);
    public static final TestType METHOD = new TestType("METHOD", 2);
    public static final TestType GROUP = new TestType("GROUP", 3);
    public static final TestType SUITE = new TestType("SUITE", 4);
    public static final TestType PATTERN = new TestType("PATTERN", 5);
    
    public final String type;
    public final int value;

    private TestType(String type, int value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public int getValue() {
        return value;
    }
    
    public static TestType valueOf(String type)
    {
        if(INVALID.type.equals(type))
        {
            return INVALID;
        }
        if(PACKAGE.type.equals(type))
        {
            return PACKAGE;
        }
        if(CLASS.type.equals(type))
        {
            return CLASS;
        }
        if(METHOD.type.equals(type))
        {
            return METHOD;
        }
        if(GROUP.type.equals(type))
        {
            return GROUP;
        }
        if(SUITE.type.equals(type))
        {
            return SUITE;
        }
        if (PATTERN.type.equals(type)) {
            return PATTERN;
        }
        throw new IllegalArgumentException("Invalid type requested " + type);
    }
}