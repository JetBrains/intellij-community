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
public class NotANumber {
    public static boolean foo() {
        double x = 0.0 / 0.0;
        boolean b1 = x < x;
        boolean b2 = x > x;
        boolean b3 = x <= x;
        boolean b4 = x >= x;
        boolean b5 = x == x;
        boolean b6 = x != x;
        return b1 || b2 || b3 || b4 || b5 || !b6;
    }

    public static boolean bar() {
        float x = 0.0f / 0.0f;
        boolean b1 = x <= x;
        boolean b2 = x >= x;
        boolean b3 = x < x;
        boolean b4 = x > x;
        boolean b5 = x == x;
        boolean b6 = x != x;
        return b1 || b2 || b3 || b4 || b5 || !b6;
    }
}