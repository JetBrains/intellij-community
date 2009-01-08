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
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
public interface PyCallExpression extends PyExpression {

  /**
   * @return the expression representing the object being called (reference to a function).
   */
  PyExpression getCallee();

  /**
   * @return ArgumentList used in the call.
   */
  @Nullable
  PyArgumentList getArgumentList();

  void addArgument(PyExpression expression);

  /**
   * Resolves callee down to particular function (standalone, method, or constructor).
   * Return's function part contains a function, never null.
   * Return's flag part marks the particulars of the call, esp. the implicit first arg situation.
   * Return is null if callee cannot be resolved. 
   */
  @Nullable
  PyMarkedFunction resolveCallee();


  enum Flag {
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
   * Couples function with a flag describing the way it is called.
   */
  class PyMarkedFunction {
    PyFunction myFunction;
    EnumSet<Flag> myFlags;
    int myImplicitOffset;

    public PyMarkedFunction(@NotNull PyFunction function, EnumSet<Flag> flags, int offset) {
      myFunction = function;
      myFlags = flags;
      myImplicitOffset = offset;
    }

    public PyFunction getFunction() {
      return myFunction;
    }

    public EnumSet<Flag> getFlags() {
      return myFlags;
    }

    /**
     * @return number of implicitly passed positional parameters; 0 means no parameters are passed implicitly.
     * Note that a <tt>*args</tt> is never markeg as passed implicitly.
     * E.g. for a function like <tt>foo(a, b, *args)</tt> always holds <tt>getImplicitOffset() < 2</tt>.   
     */
    public int getImplicitOffset() {
      return myImplicitOffset;
    }

  }

}
