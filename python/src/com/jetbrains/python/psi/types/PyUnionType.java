package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyUnionType implements PyType {
  private final List<PyType> myMembers;

  public PyUnionType(Collection<PyType> members) {
    myMembers = new ArrayList<PyType>(members);
  }

  @Nullable
  public List<? extends PsiElement> resolveMember(String name,
                                                  PyExpression location,
                                                  AccessDirection direction,
                                                  PyResolveContext resolveContext) {
    SmartList<PsiElement> ret = new SmartList<PsiElement>();
    boolean all_nulls = true;
    for (PyType member : myMembers) {
      if (member != null) {
        List<? extends PsiElement> result = member.resolveMember(name, null, direction, resolveContext);
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
   */
  @Override
  public boolean isBuiltin() {
    for (PyType one : myMembers) {
      if (one == null || !one.isBuiltin()) return false;
    }
    return true;
  }

  @Nullable
  public static PyType union(PyType type1, PyType type2) {
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

  public boolean isWeak() {
    for (PyType member : myMembers) {
      if (member == null) {
        return true;
      }
    }
    return false;
  }

  public List<PyType> getMembers() {
    return myMembers;
  }
}
