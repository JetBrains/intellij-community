package com.jetbrains.python.codeInsight.stdlib;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyNamedTupleType extends PyClassTypeImpl implements PyCallableType {
  private final String myName;
  private final boolean myDefinition;
  private final PsiElement myDeclaration;
  private final List<String> myFields;

  private static final ImmutableSet<String> ourClassMembers = ImmutableSet.of("_fields", "_make");
  private static final ImmutableSet<String> ourInstanceMembers = ImmutableSet.of("_asdict", "_replace");

  public PyNamedTupleType(PyClass tupleClass, PsiElement declaration, String name, List<String> fields, boolean isDefinition) {
    super(tupleClass, isDefinition);
    myDeclaration = declaration;
    myFields = fields;
    myName = name;
    myDefinition = isDefinition;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    final List<? extends RatedResolveResult> classMembers = super.resolveMember(name, location, direction, resolveContext);
    if (classMembers != null && !classMembers.isEmpty()) {
      return classMembers;
    }
    if (hasField(name)) {
      return Collections.singletonList(new RatedResolveResult(1000, new PyElementImpl(myDeclaration.getNode())));
    }
    return Collections.emptyList();
  }

  private boolean hasField(String name) {
    if (myFields.contains(name)) {
      return true;
    }
    if (myDefinition) {
      return ourClassMembers.contains(name);
    }
    else {
      return ourInstanceMembers.contains(name);
    }
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    List<Object> result = new ArrayList<Object>();
    Collections.addAll(result, super.getCompletionVariants(completionPrefix, location, context));
    for (String field : myFields) {
      result.add(LookupElementBuilder.create(field));
    }
    for (String s : myDefinition ? ourClassMembers : ourInstanceMembers) {
      result.add(LookupElementBuilder.create(s));
    }
    return ArrayUtil.toObjectArray(result);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    if (myDefinition) {
      return new PyNamedTupleType(myClass, myDeclaration, myName, myFields, false);
    }
    return null;
  }

  @Override
  public PyClassType toInstance() {
    return myIsDefinition ? new PyNamedTupleType(myClass, myDeclaration, myName, myFields, false) : this;
  }

  @Override
  public String toString() {
    return "PyNamedTupleType: " + myName;
  }

  @Nullable
  public static PyType fromCall(PyCallExpression call) {
    final String name = PyPsiUtils.strValue(call.getArgument(0, PyExpression.class));
    final PyExpression fieldNamesExpression = PyPsiUtils.flattenParens(call.getArgument(1, PyExpression.class));
    if (name == null || fieldNamesExpression == null) {
      return null;
    }
    List<String> fieldNames = null;
    if (fieldNamesExpression instanceof PySequenceExpression) {
      fieldNames = PyUtil.strListValue(fieldNamesExpression);
    }
    else {
      final String fieldNamesString = PyPsiUtils.strValue(fieldNamesExpression);
      if (fieldNamesString != null) {
        fieldNames = parseFieldNamesString(fieldNamesString);
      }
    }
    if (fieldNames != null) {
      PyClass tuple = PyBuiltinCache.getInstance(call).getClass(PyNames.TUPLE);
      if (tuple != null) {
        return new PyNamedTupleType(tuple, call, name, fieldNames, true);
      }
    }
    return null;
  }

  private static List<String> parseFieldNamesString(String fieldNamesString) {
    List<String> result = new ArrayList<String>();
    for(String name: StringUtil.tokenize(fieldNamesString, ", ")) {
      result.add(name);
    }
    return result;
  }

  public int getElementCount() {
    return myFields.size();
  }
}
