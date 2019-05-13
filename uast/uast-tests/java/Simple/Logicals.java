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
public class Logicals {
    public static boolean foo() {
        int one = 1;
        int two = 2;
        int three = 3;
        int four = 4;
        boolean b1 = two > one && four > three;
        boolean b2 = one > two && four > three;
        boolean b3 = b1 || b2;
        boolean b4 = two > one || three > four;
        return b1 && !b2 && b3 && b4;
    }
}