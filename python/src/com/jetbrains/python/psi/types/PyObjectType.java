package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.psi.impl.PyBuiltinCache;

import java.util.*;


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
    super(source);
  } 
  
  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY; // TODO: implement
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
