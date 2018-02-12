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
import java.util.*;

public class For {

    public static List<Integer> getList(int size) {
        List<Integer> result = new LinkedList<Integer>();
        int a = 0;
        for (int i = a++; i < size; i++) {
            result.add(i);
        }
        result.add(a);
        return result;
    }

    public static int sum(List<Integer> numbers) {
        int result = 0;
        int size = 3;
        for (int number: getList(++size)) {
            result = result + number;
        }
        return result + size;
    }
}