package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// TODO: eliminate this class

/**
 * Represents Python 'object' type.
 * User: dcheryasov
 * Date: Jun 6, 2008
 * Time: 8:39:17 AM
 */
public class PyObjectType extends PyClassType implements PyType {

  protected PyClass myClass;

  protected static Set<String> ourPossibleFields;
  static {
    ourPossibleFields = new HashSet<String>();
    ourPossibleFields.add("__class__");
    ourPossibleFields.add("__doc__");
    ourPossibleFields = Collections.unmodifiableSet(ourPossibleFields); 
  }

  public PyObjectType(PyClass source) {
    super(source, false);
  } 
  
  /**
   * Sometimes we can make a reasonable guess that a name is an instance member while we cannot directly resolve it.
   * All such guesses are listed by this method.
   * @return a set of possible instance member names.
   */
  @NotNull
  public Set<String> getPossibleInstanceMembers() {
    return ourPossibleFields; 
  }
  
}
