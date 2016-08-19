/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.util;

import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

public interface Copyable<T> {
    T copy();

    class Util {
        private Util() { }

        @SuppressWarnings({"unchecked"})
        public static <T extends Copyable> List<T> copy(Collection<T> stuff) {
            final List<Copyable<T>> l = new ArrayList<>(stuff.size());
            for (Copyable<T> copyable : stuff) {
                if (copyable != null) l.add(copyable.copy());
            }
            return (List<T>)l;
        }
    }
}
