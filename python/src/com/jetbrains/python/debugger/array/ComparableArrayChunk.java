/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.array;

/**
 * @author amarch
 */
public class ComparableArrayChunk extends ArrayChunk implements Comparable<ComparableArrayChunk> {
  public ComparableArrayChunk(String baseSlice, int rows, int columns, int rOffset, int cOffset) {
    super(baseSlice, rows, columns, rOffset, cOffset);
  }

  @Override
  void fillData(Runnable callback) {
    return;
  }

  @Override
  public int compareTo(ComparableArrayChunk other) {
    int compRow = rOffset - other.rOffset;
    int compCol = cOffset - other.cOffset;

    if (compRow != 0) {
      return compRow;
    }
    else {
      return compCol;
    }
  }
}
