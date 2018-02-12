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
public class IdentityEquals {
    public static boolean foo() {
        Integer i1 = 111;
        Integer i2 = 222;
        Integer i12 = i1 + i2;
        Integer i21 = i2 + i1;
        return i12 == i21;
    }

    public static boolean bar() {
        String s1 = "hello";
        String s2 = s1 + s1;
        String s3 = "hellohello";
        return s2 == s3;
    }
}