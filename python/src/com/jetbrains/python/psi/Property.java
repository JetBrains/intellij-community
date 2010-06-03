package com.jetbrains.python.psi;

import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a property, result of either a call to property() or application of @property and friends.
 * This is <i>not</i> a node of PSI tree.
 * <br/>
 * User: dcheryasov
 * Date: May 31, 2010 5:18:10 PM
 */
public interface Property {
  /**
   * @return the name of this property
   */
  @NotNull
  PyQualifiedName getQualifiedName();

  /**
   * @return the setter (usually a method)
   */
  @Nullable
  Callable getSetter();

  /**
   * @return the getter (usually a method)
   */
  @Nullable
  Callable getGetter();

  /**
   * @return the deleter (usually a method)
   */
  @Nullable
  Callable getDeleter();

  /**
   * @return doc comment, explicit if present, else from getter.
   */
  @Nullable
  String getDoc();

}
