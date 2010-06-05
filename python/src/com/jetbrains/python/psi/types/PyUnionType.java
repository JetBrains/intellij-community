package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
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

  @NotNull
  public Maybe<? extends PsiElement> resolveMember(String name, Context context) {
    for (PyType member : myMembers) {
      Maybe<? extends PsiElement> result = member.resolveMember(name, context);
      if (result.valueOrNull() != null) {
        return result;
      }
    }
    return NOT_RESOLVED_YET; // NOTE: or UNRESOLVED ?
  }

  public Object[] getCompletionVariants(PyQualifiedExpression referenceExpression, ProcessingContext context) {
    Set<Object> variants = new HashSet<Object>();
    for (PyType member : myMembers) {
      Collections.addAll(variants, member.getCompletionVariants(referenceExpression, context));
    }
    return variants.toArray(new Object[variants.size()]);
  }

  public String getName() {
    return "one of (" + StringUtil.join(myMembers, new NullableFunction<PyType, String>() {
      public String fun(PyType pyType) {
        return pyType.getName();
      }
    }, ", ") + ")";
  }

  @Nullable
  public static PyType union(PyType type1, PyType type2) {
    if (type1 == null || type2 == null) {
      return null;
    }
    if (type1 instanceof PyTupleType && type2 instanceof PyTupleType) {
      final PyTupleType tupleType1 = (PyTupleType)type1;
      final PyTupleType tupleType2 = (PyTupleType)type2;
      if (tupleType1.getElementCount() == tupleType2.getElementCount()) {
        int count = tupleType1.getElementCount();
        PyType[] members = new PyType[count];
        for (int i = 0; i < count; i++) {
          members [i] = union(tupleType1.getElementType(i), tupleType2.getElementType(i));
        }
        return new PyTupleType(tupleType1, members);
      }
    }
    Set<PyType> members = new HashSet<PyType>();
    if (type1 instanceof PyUnionType) {
      members.addAll(((PyUnionType) type1).myMembers);
    }
    else {
      members.add(type1);
    }
    if (type2 instanceof PyUnionType) {
      members.addAll(((PyUnionType) type2).myMembers);
    }
    else {
      members.add(type2);
    }
    if (members.size() == 1) {
      return members.iterator().next();
    }
    return new PyUnionType(members);
  }
}
