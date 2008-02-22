/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.TextRange;

public class EvaluatedTextRange {
  private TextRange range;
  private String value;

  public EvaluatedTextRange(TextRange range, String value) {
    this.range = range;
    this.value = value;
  }

  public EvaluatedTextRange(TextRange range, char value) {
    this(range, new String(new char[]{value}));
  }

  public TextRange getRange() {
    return range;
  }

  public String getValue() {
    return value;
  }


  public String toString() {
    return "EvaluatedTextRange[" + range + "]: " + value;
  }
}
