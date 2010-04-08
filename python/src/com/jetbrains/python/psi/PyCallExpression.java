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

  /**
   * @return The array of call arguments, or an empty array if the call has no argument list.
   */
  @NotNull
  PyExpression[] getArguments();

  void addArgument(PyExpression expression);

  /**
   * Resolves callee down to particular function (standalone, method, or constructor).
   * Return's function part contains a function, never null.
   * Return's flag part marks the particulars of the call, esp. the implicit first arg situation.
   * Return is null if callee cannot be resolved. 
   */
  @Nullable
  PyMarkedFunction resolveCallee();

  /**
   * Checks if the unqualified name of the callee matches the specified text.
   *
   * @param name the text to check
   * @return true if matches, false otherwise
   */
  boolean isCalleeText(@NotNull String name);

  /**
   * Couples function with a flag describing the way it is called.
   */
  class PyMarkedFunction {
    PyFunction myFunction;
    EnumSet<PyFunction.Flag> myFlags;
    int myImplicitOffset;

    public PyMarkedFunction(@NotNull PyFunction function, EnumSet<PyFunction.Flag> flags, int offset) {
      myFunction = function;
      myFlags = flags;
      myImplicitOffset = offset;
    }

    public PyFunction getFunction() {
      return myFunction;
    }

    public EnumSet<PyFunction.Flag> getFlags() {
      return myFlags;
    }

    /**
     * @return number of implicitly passed positional parameters; 0 means no parameters are passed implicitly.
     * Note that a <tt>*args</tt> is never marked as passed implicitly.
     * E.g. for a function like <tt>foo(a, b, *args)</tt> always holds <tt>getImplicitOffset() < 2</tt>.   
     */
    public int getImplicitOffset() {
      return myImplicitOffset;
    }

  }

}
