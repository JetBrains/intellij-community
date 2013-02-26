/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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

package com.pme.exe.res;

import com.pme.exe.Bin;

/**
 * Date: Apr 20, 2006
 * Time: 4:32:29 PM
 */
public class ValuesAdd extends Bin.DWord {
  private Bin.Value myActual;
  private Bin.Value myMinus;

  public ValuesAdd(Bin.Value actual, Bin.Value minusConst) {
    super(actual.getName());
    myActual = actual;
    myMinus = minusConst;
  }

  public Value setValue(long value) {
    return myActual.setValue(value + myMinus.getValue());
  }

  public long getValue() {
    return myActual.getValue() - myMinus.getValue();
  }
}
