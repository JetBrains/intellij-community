package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyUnionType implements PyType {
  private final Set<PyType> myMembers;

  private PyUnionType(Collection<PyType> members) {
    myMembers = new LinkedHashSet<PyType>(members);
  }

  @Nullable
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    SmartList<RatedResolveResult> ret = new SmartList<RatedResolveResult>();
    boolean all_nulls = true;
    for (PyType member : myMembers) {
      if (member != null) {
        List<? extends RatedResolveResult> result = member.resolveMember(name, null, direction, resolveContext);
        if (result != null) {
          all_nulls = false;
          ret.addAll(result);
        }
      }
    }
    return all_nulls ? null : ret;
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    Set<Object> variants = new HashSet<Object>();
    for (PyType member : myMembers) {
      if (member != null) {
        Collections.addAll(variants, member.getCompletionVariants(completionPrefix, location, context));
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }

  public String getName() {
    return "one of (" + StringUtil.join(myMembers, new NullableFunction<PyType, String>() {
      public String fun(PyType pyType) {
        return pyType == null ? "unknown" : pyType.getName();
      }
    }, ", ") + ")";
  }

  /**
   * @return true if all types in the union are built-in.
   * @param context
   */
  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    for (PyType one : myMembers) {
      if (one == null || !one.isBuiltin(context)) return false;
    }
    return true;
  }

  @Override
  public void assertValid() {
    for (PyType member : myMembers) {
      if (member != null) {
        member.assertValid();
      }
    }
  }

  @Nullable
  public static PyType union(@Nullable PyType type1, @Nullable PyType type2) {
    if (type1 instanceof PyTupleType && type2 instanceof PyTupleType) {
      final PyTupleType tupleType1 = (PyTupleType)type1;
      final PyTupleType tupleType2 = (PyTupleType)type2;
      if (tupleType1.getElementCount() == tupleType2.getElementCount()) {
        int count = tupleType1.getElementCount();
        PyType[] members = new PyType[count];
        for (int i = 0; i < count; i++) {
          members[i] = union(tupleType1.getElementType(i), tupleType2.getElementType(i));
        }
        return new PyTupleType(tupleType1, members);
      }
    }
    Set<PyType> members = new LinkedHashSet<PyType>();
    if (type1 instanceof PyUnionType) {
      members.addAll(((PyUnionType)type1).myMembers);
    }
    else {
      members.add(type1);
    }
    if (type2 instanceof PyUnionType) {
      members.addAll(((PyUnionType)type2).myMembers);
    }
    else {
      members.add(type2);
    }
    if (members.size() == 1) {
      return members.iterator().next();
    }
    return new PyUnionType(members);
  }

  @Nullable
  public static PyType union(Collection<PyType> members) {
    final int n = members.size();
    if (n == 0) {
      return null;
    }
    else if (n == 1) {
      return members.iterator().next();
    }
    else {
      final Iterator<PyType> it = members.iterator();
      PyType res = unit(it.next());
      while(it.hasNext()) {
        res = union(res, it.next());
      }
      return res;
    }
  }

  public boolean isWeak() {
    for (PyType member : myMembers) {
      if (member == null) {
        return true;
      }
    }
    return false;
  }

  public Collection<PyType> getMembers() {
    return myMembers;
  }

  @Nullable
  public PyType exclude(PyType t, TypeEvalContext context) {
    final List<PyType> members = new ArrayList<PyType>();
    for (PyType m : getMembers()) {
      if (!PyTypeChecker.match(t, m, context)) {
        members.add(m);
      }
    }
    return union(members);
  }

  private static PyType unit(@Nullable PyType type) {
    if (type instanceof PyUnionType) {
      Set<PyType> members = new LinkedHashSet<PyType>();
      members.addAll(((PyUnionType)type).getMembers());
      return new PyUnionType(members);
    }
    else {
      return new PyUnionType(Collections.singletonList(type));
    }
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PyUnionType) {
      final PyUnionType otherType = (PyUnionType)other;
      return myMembers.equals(otherType.myMembers);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myMembers.hashCode();
  }
}
