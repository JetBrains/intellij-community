package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyNamedTupleType implements PyCallableType {
  private final String myName;
  private final boolean myDefinition;
  private final PsiElement myDeclaration;
  private final List<String> myFields;

  public PyNamedTupleType(PsiElement declaration, String name, List<String> fields, boolean isDefinition) {
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
    if (hasField(name)) {
      return Collections.singletonList(new RatedResolveResult(1000, new PyElementImpl(myDeclaration.getNode())));
    }
    return Collections.emptyList();
  }

  private boolean hasField(String name) {
    if (myDefinition) {
      return "_make".equals(name);
    }
    else {
      return myFields.contains(name) || "_replace".equals(name);
    }
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    if (!myDefinition) {
      for (String field : myFields) {
        result.add(LookupElementBuilder.create(field));
      }
    }
    return ArrayUtil.toObjectArray(result);
  }

  @Override
  public String getName() {
    return "namedtuple '" + myName + "'";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public PyType getCallType() {
    if (myDefinition) {
      return new PyNamedTupleType(myDeclaration, myName, myFields, false);
    }
    return null;
  }

  @Nullable
  public static PyType fromCall(PyCallExpression call) {
    final String name = PyUtil.strValue(call.getArgument(0, PyExpression.class));
    final PyExpression fieldNamesExpression = PyUtil.flattenParens(call.getArgument(1, PyExpression.class));
    if (name == null || fieldNamesExpression == null) {
      return null;
    }
    List<String> fieldNames = null;
    if (fieldNamesExpression instanceof PySequenceExpression) {
      fieldNames = PyUtil.strListValue(fieldNamesExpression);
    }
    else {
      final String fieldNamesString = PyUtil.strValue(fieldNamesExpression);
      if (fieldNamesString != null) {
        fieldNames = parseFieldNamesString(fieldNamesString);
      }
    }
    if (fieldNames != null) {
      return new PyNamedTupleType(call, name, fieldNames, true);
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
}
