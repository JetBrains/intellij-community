// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 4:18:29 PM
 */
public class ValuesSub extends Bin.DWord {
  private Bin.Value myActual;
  private Bin.Value myMinus;

  public ValuesSub(Bin.Value actual, Bin.Value minusConst) {
    super(actual.getName());
    myActual = actual;
    myMinus = minusConst;
  }

  public Value setValue(long value) {
    return myActual.setValue(value - myMinus.getValue());
  }

  public long getValue() {
    return myActual.getValue() + myMinus.getValue();
  }
}
