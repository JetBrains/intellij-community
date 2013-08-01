package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
public class PyDynamicallyEvaluatedType extends PyUnionType {
  private PyDynamicallyEvaluatedType(@NotNull Collection<PyType> members) {
    super(members);
  }

  @NotNull
  public static PyDynamicallyEvaluatedType create(@NotNull PyType type) {
    final List<PyType> members = new ArrayList<PyType>();
    if (type instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)type;
      members.addAll(unionType.getMembers());
      if (!unionType.isWeak()) {
        members.add(null);
      }
    }
    else {
      members.add(type);
      members.add(null);
    }
    return new PyDynamicallyEvaluatedType(members);
  }

  @Override
  public String getName() {
    PyType res = excludeNull();
    return res != null ? res.getName() : PyNames.UNKNOWN_TYPE;
  }
}
