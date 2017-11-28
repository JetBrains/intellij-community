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
public class ByteShort {
    public static int foo() {
        byte b1 = 100;
        short s2 = 2;
        byte b11 = b1;
        short s21 = s2;
        int i3 = b11 + b1;
        int i4 = s21 + s2;
        int i5 = b11 + s21;
        byte b3 = (byte) i3;
        short s4 = (short) i4;
        return i5 + b3 + s4;
    }
}