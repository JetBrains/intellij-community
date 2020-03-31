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
public class Lambda {
    public static int foo() {
        int variable = 42;

        Runnable runnable = () -> {
            int variable1 = 24;
            variable1++;
        };
        runnable.run();

        return variable;
    }

    public static int bar() throws Exception {
        int variable = 42;

        Callable<Integer> callable1 = () -> variable * 2;
        callable1.call();

        Callable<Integer> callable2 = () -> {
            int a = 5;
            return variable + a;
        };
        callable2.call();

        return variable;
    }
}