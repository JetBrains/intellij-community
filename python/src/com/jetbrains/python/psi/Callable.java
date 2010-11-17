package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something that can be called, passed parameters to, and return something back.
 * <br/>
 * User: dcheryasov
 * Date: May 15, 2010 3:22:30 PM
 */
public interface Callable extends PyElement {

  /**
   * @return a list of parameters passed to this callable, possibly empty.
   */
  @NotNull
  PyParameterList getParameterList();

  /**
   * @return the type of returned value.
   */
  @Nullable
  PyType getReturnType(TypeEvalContext typeEvalContext);

  /**
   * @return a methods returns itself, non-method callables return null.
   */
  @Nullable
  PyFunction asMethod();

}
