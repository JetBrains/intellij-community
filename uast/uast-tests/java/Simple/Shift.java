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
public class Shift {
    public static int foo() {
        int one = 1;
        int two = one << 1;
        int sixteen = two << 3;
        int minInt = 0x80000000;
        int quarter = minInt >> 2;
        int unsignedQuarter = minInt >>> 2;

        return sixteen + quarter + unsignedQuarter;
    }

    public static long bar() {
        long one = 1L;
        long two = one << 1;
        long large = two << 61;
        long minLong = 0x8000000000000000L;
        long eighth = minLong >> 3;
        long unsignedEighth = minLong >>> 3;

        return large + eighth + unsignedEighth;
    }
}