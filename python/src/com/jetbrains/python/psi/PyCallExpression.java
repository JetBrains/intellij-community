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

package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 13:39:13
 * To change this template use File | Settings | File Templates.
 */
public interface PyCallExpression extends PyExpression {
  PyExpression getCallee();
  PyArgumentList getArgumentList();

  void addArgument(PyExpression expression);

  /**
   * Resolves callee down to particular function (standalone, method, or constructor).
   * Return's function part contains a function, never null.
   * Return's flag part is true if the function call is qualified by an instance, or is a constructor call.
   * Return is null if callee cannot be resolved. 
   */
  @Nullable
  PyMarkedFunction resolveCallee();

  /**
   * @return true if the call is a method call qualified by class instance. <b>Note:</b> Constructor calls return false.
   */
  //boolean isByInstance();

  enum Flag {
    /**
     * First arg of the call is implicit, drop first parameter.
     */
    IMPLICIT_FIRST_ARG,
    /**
     * Called function is decorated with @classmethod, first param is the class.
     */
    CLASSMETHOD,
    /**
     * Called function is decorated with @staticmethod, first param is as in a regular function.
     */
    STATICMETHOD
  }

  /**
   * Couples function with a flag showing if it is being called as an instance method.
   */
  class PyMarkedFunction {
    PyFunction myFunction;
    boolean myIsInstanceCall;
    EnumSet<Flag> myFlags;

    public PyMarkedFunction(@NotNull PyFunction function, EnumSet<Flag> flags) {
      myFunction = function;
      //myIsInstanceCall = instance;
      myFlags = flags;
    }

    public PyFunction getFunction() {
      return myFunction;
    }

    public EnumSet<Flag> getFlags() {
      return myFlags;
    }

    /*
    public boolean isInstanceCall() {
      return myIsInstanceCall;
    }
    */
  }

}
