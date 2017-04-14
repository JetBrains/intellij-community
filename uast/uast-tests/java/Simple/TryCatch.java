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
public class TryCatch {
    public static int foo(String str) {
        int sum = 0;
        for (String part: str.split(" ")) {
            int b = 0;
            try {
                sum = sum + Integer.parseInt(part);
                b = 1;
            }
            catch (NumberFormatException ex) {
                b = 1;
            }
            int c = b;
        }
        return sum;
    }
}