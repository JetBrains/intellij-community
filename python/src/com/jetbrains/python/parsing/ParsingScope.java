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
package com.jetbrains.python.parsing;

/**
 * @author vlan
 */
public class ParsingScope {
  private boolean myFunction = false;
  private boolean myClass = false;
  private boolean mySuite = false;
  private boolean myAfterSemicolon = false;
  private boolean myAsync = false;

  protected ParsingScope() {}

  public ParsingScope withFunction(boolean flag) {
    final ParsingScope result = copy();
    result.myFunction = flag;
    return result;
  }

  public ParsingScope withClass(boolean flag) {
    final ParsingScope result = copy();
    result.myClass = flag;
    return result;
  }

  public ParsingScope withSuite(boolean flag) {
    final ParsingScope result = copy();
    result.mySuite = flag;
    return result;
  }

  public ParsingScope withAsync() {
    final ParsingScope result = copy();
    result.myAsync = true;
    return result;
  }

  public boolean isFunction() {
    return myFunction;
  }

  public boolean isClass() {
    return myClass;
  }

  public boolean isSuite() {
    return mySuite;
  }

  public boolean isAsync() {
    return myAsync;
  }

  public boolean isAfterSemicolon() {
    return myAfterSemicolon;
  }

  public void setAfterSemicolon(boolean value) {
    myAfterSemicolon = value;
  }

  protected ParsingScope createInstance() {
    return new ParsingScope();
  }

  protected ParsingScope copy() {
    final ParsingScope result = createInstance();
    result.myFunction = myFunction;
    result.myClass = myClass;
    result.mySuite = mySuite;
    result.myAsync = myAsync;
    return result;
  }
}
