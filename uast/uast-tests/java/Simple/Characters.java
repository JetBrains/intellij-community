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
public class Characters {
    public static char foo() {
        char a = 'a';
        char c = (char) (a + 2);
        char f = (char) (c + 3);
        char d = (char) (f - 2);
        int diff = f - a;
        int aa = a + a;
        char cdiff = (char) diff;
        return d;
    }
}