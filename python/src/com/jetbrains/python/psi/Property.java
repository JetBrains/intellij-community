package com.jetbrains.python.psi;

import com.jetbrains.python.toolbox.Maybe;
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
  String getName();

  /**
   * @return the setter: a method or null if defined, or something else callable if undefined.
   */
  @NotNull
  Maybe<PyFunction> getSetter();

  /**
   * @return the getter: a method or null if defined, or something else callable if undefined.
   */
  @NotNull
  Maybe<PyFunction> getGetter();

  /**
   * @return the deleter: a method or null if defined, or something else callable if undefined.
   */
  @NotNull
  Maybe<PyFunction> getDeleter();

  /**
   * @return doc string as known to property() call. If null, see getter's doc.
   */
  @Nullable
  String getDoc();

  /**
   * @return the target to which the result of property() call is assigned. For things defined via @property, it is null. 
   */
  @Nullable
  PyTargetExpression getDefinitionSite();

  /**
   * @param direction how the property is accessed
   * @return getter, setter, or deleter.
   */
  @NotNull
  Maybe<PyFunction> getByDirection(@NotNull AccessDirection direction);

}
