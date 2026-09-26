// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.eval4j.test;

import java.lang.reflect.Array;

public final class ArrayHelper {
    public static Object newMultiArray(Class<?> elementType, Integer... dimensions) {
        int[] dims = new int[dimensions.length];
        int i = 0;
        for (Integer dimension : dimensions) {
            dims[i] = dimension;
            i++;
        }

        return Array.newInstance(elementType, dims);
    }
}
