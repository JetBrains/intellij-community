package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.TypeEvalContext;
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
  @Nullable
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
   *
   * @param context the context for evaluating types
   */
  @Nullable
  PyMarkedCallee resolveCallee(TypeEvalContext context);

  /**
   * Checks if the unqualified name of the callee matches any of the specified names
   *
   * @param nameCandidates the names to check
   * @return true if matches, false otherwise
   */
  boolean isCalleeText(@NotNull String... nameCandidates);

  /**
   * Couples function with a flag describing the way it is called.
   */
  class PyMarkedCallee {
    Callable myCallable;
    EnumSet<PyFunction.Flag> myFlags;
    int myImplicitOffset;
    boolean myImplicitlyResolved;

    /**
     * Method-oriented constructor.
     * @param function the method (or any other callable, but why bother then).
     * @param flags result of decorators or wrapping.
     * @param offset implicit argument offset; parameters up to this are implicitly filled in the call.
     * @param implicitlyResolved value for {@link #isImplicitlyResolved()}
     */
    public PyMarkedCallee(@NotNull Callable function, EnumSet<PyFunction.Flag> flags, int offset, boolean implicitlyResolved) {
      myCallable = function;
      myFlags = flags;
      myImplicitOffset = offset;
      myImplicitlyResolved = implicitlyResolved;
    }

    public PyMarkedCallee(Callable callable, boolean implicitlyResolved) {
      myCallable = callable;
      myFlags = EnumSet.noneOf(PyFunction.Flag.class);
      myImplicitOffset = 0;
      myImplicitlyResolved = implicitlyResolved; 
    }

    public Callable getCallable() {
      return myCallable;
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

    /**
     * @return true iff the result is resolved based on divination and name similarity rather than by proper resolution process.
     */
    public boolean isImplicitlyResolved() {
      return myImplicitlyResolved;
    }
  }
}
