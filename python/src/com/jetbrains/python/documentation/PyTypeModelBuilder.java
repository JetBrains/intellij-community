/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.ChainIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.$;
import static com.jetbrains.python.documentation.DocumentationBuilderKit.combUp;

/**
 * @author traff
 */
public class PyTypeModelBuilder {
  private final Map<PyType, TypeModel> myVisited = Maps.newHashMap();
  private final TypeEvalContext myContext;

  PyTypeModelBuilder(TypeEvalContext context) {
    this.myContext = context;
  }

  abstract static class TypeModel {
    abstract void accept(TypeVisitor visitor);

    public String asString() {
      TypeToStringVisitor visitor = new TypeToStringVisitor();
      this.accept(visitor);
      return visitor.getString();
    }

    public void toBodyWithLinks(@NotNull ChainIterable<String> body, @NotNull PsiElement anchor) {
      TypeToBodyWithLinksVisitor visitor = new TypeToBodyWithLinksVisitor(body, anchor);
      this.accept(visitor);
    }
  }

  static class OneOf extends TypeModel {
    private Collection<TypeModel> oneOfTypes;

    private OneOf(Collection<TypeModel> oneOfTypes) {
      this.oneOfTypes = oneOfTypes;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.oneOf(this);
    }
  }

  static class CollectionOf extends TypeModel {
    private String collectionName;
    private List<TypeModel> elementTypes;

    private CollectionOf(String collectionName, List<TypeModel> elementTypes) {
      this.collectionName = collectionName;
      this.elementTypes = elementTypes;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.collectionOf(this);
    }
  }

  static class NamedType extends TypeModel {
    private String name;

    private NamedType(String name) {
      this.name = name;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.name(this.name);
    }
  }

  static class UnknownType extends TypeModel {
    private final TypeModel type;

    private UnknownType(TypeModel type) {
      this.type = type;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.unknown(this);
    }
  }

  static class OptionalType extends TypeModel {
    private final TypeModel type;

    private OptionalType(TypeModel type) {
      this.type = type;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.optional(this);
    }
  }

  static class TupleType extends TypeModel {
    private final List<TypeModel> members;
    private final boolean homogeneous;

    public TupleType(List<TypeModel> members, boolean homogeneous) {
      this.members = members;
      this.homogeneous = homogeneous;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.tuple(this);
    }
  }

  private static TypeModel _(String name) {
    return new NamedType(name);
  }

  static class FunctionType extends TypeModel {
    private TypeModel returnType;
    @Nullable private Collection<TypeModel> parameters;

    FunctionType(@Nullable TypeModel returnType, @Nullable Collection<TypeModel> parameters) {
      if (returnType != null) {
        this.returnType = returnType;
      }
      else {
        this.returnType = _(PyNames.UNKNOWN_TYPE);
      }
      this.parameters = parameters;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.function(this);
    }
  }

  static class ParamType extends TypeModel {
    @Nullable private final String name;
    @Nullable private final TypeModel type;


    private ParamType(@Nullable String name, @Nullable TypeModel type) {
      this.name = name;
      this.type = type;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.param(this);
    }
  }

  /**
   * Builds tree-like type model for PyType
   *
   * @param type
   * @param allowUnions
   * @return
   */
  public TypeModel build(@Nullable PyType type,
                         boolean allowUnions) {
    final TypeModel evaluated = myVisited.get(type);
    if (evaluated != null) {
      return evaluated;
    }
    if (myVisited.containsKey(type)) { //already evaluating?
      return type != null ? _(type.getName()) : _(PyNames.UNKNOWN_TYPE);
    }
    myVisited.put(type, null); //mark as evaluating

    TypeModel result = null;
    if (type instanceof PyCollectionType) {
      final String name = type.getName();
      final List<PyType> elementTypes = ((PyCollectionType)type).getElementTypes(myContext);
      boolean nullOnlyTypes = true;
      for (PyType elementType : elementTypes) {
        if (elementType != null) {
          nullOnlyTypes = false;
          break;
        }
      }
      final List<TypeModel> elementModels = new ArrayList<>();
      if (!nullOnlyTypes) {
        for (PyType elementType : elementTypes) {
          elementModels.add(build(elementType, true));
        }
        if (!elementModels.isEmpty()) {
          result = new CollectionOf(name, elementModels);
        }
      }
    }
    else if (type instanceof PyUnionType && allowUnions) {
      final PyUnionType unionType = (PyUnionType)type;
      if (type instanceof PyDynamicallyEvaluatedType || PyTypeChecker.isUnknown(type)) {
        result = new UnknownType(build(unionType.excludeNull(myContext), true));
      }
      else {
        result = Optional
          .ofNullable(getOptionalType(unionType))
          .<PyTypeModelBuilder.TypeModel>map(optionalType -> new OptionalType(build(optionalType, true)))
          .orElseGet(() -> new OneOf(Collections2.transform(unionType.getMembers(), t -> build(t, false))));
      }
    }
    else if (type instanceof PyCallableType && !(type instanceof PyClassLikeType)) {
      result = build((PyCallableType)type);
    }
    else if (type instanceof PyTupleType) {
      final List<TypeModel> elementModels = new ArrayList<>();
      final PyTupleType tupleType = (PyTupleType)type;
      for (int i = 0; i < (tupleType.isHomogeneous() ? 1 : tupleType.getElementCount()); i++) {
        final PyType elementType = tupleType.getElementType(i);
        elementModels.add(build(elementType, true));
      }
      result = new TupleType(elementModels, tupleType.isHomogeneous());
    }
    if (result == null) {
      result = type != null ? _(type.getName()) : _(PyNames.UNKNOWN_TYPE);
    }
    myVisited.put(type, result);
    return result;
  }

  @Nullable
  private static PyType getOptionalType(@NotNull PyUnionType type) {
    final Collection<PyType> members = type.getMembers();
    if (members.size() == 2) {
      boolean foundNone = false;
      PyType optional = null;
      for (PyType member : members) {
        if (PyNoneType.INSTANCE.equals(member)) {
          foundNone = true;
        }
        else if (member != null) {
          optional = member;
        }
      }
      if (foundNone) {
        return optional;
      }
    }
    return null;
  }

  private TypeModel build(@NotNull PyCallableType type) {
    List<TypeModel> parameterModels = null;
    final List<PyCallableParameter> parameters = type.getParameters(myContext);
    if (parameters != null) {
      parameterModels = new ArrayList<>();
      for (PyCallableParameter parameter : parameters) {
        parameterModels.add(new ParamType(parameter.getName(), build(parameter.getType(myContext), true)));
      }
    }
    final PyType ret = type.getReturnType(myContext);
    final TypeModel returnType = build(ret, true);
    return new FunctionType(returnType, parameterModels);
  }

  private interface TypeVisitor {
    void oneOf(OneOf oneOf);

    void collectionOf(CollectionOf collectionOf);

    void name(String name);

    void function(FunctionType type);

    void param(ParamType text);

    void unknown(UnknownType type);

    void optional(OptionalType type);

    void tuple(TupleType type);
  }

  private static class TypeToStringVisitor extends TypeNameVisitor {
    private final StringBuilder myStringBuilder = new StringBuilder();

    @Override
    protected void add(String s) {
      myStringBuilder.append(s);
    }

    @Override
    protected void addType(String name) {
      add(name);
    }

    public String getString() {
      return myStringBuilder.toString();
    }

    @Override
    public void unknown(UnknownType type) {
      final TypeModel nested = type.type;
      if (nested != null) {
        add("Union[");
        nested.accept(this);
        add(", " + PyNames.UNKNOWN_TYPE);
        add("]");
      }
    }
  }

  private static class TypeToBodyWithLinksVisitor extends TypeNameVisitor {
    private ChainIterable<String> myBody;
    private PsiElement myAnchor;

    public TypeToBodyWithLinksVisitor(ChainIterable<String> body, PsiElement anchor) {
      myBody = body;
      myAnchor = anchor;
    }

    @Override
    protected void add(String s) {
      myBody.addItem(combUp(s));
    }

    @Override
    protected void addType(String name) {
      PyType type = PyTypeParser.getTypeByName(myAnchor, name);
      if (type instanceof PyClassType) {
        myBody.addWith(new DocumentationBuilderKit.LinkWrapper(PythonDocumentationProvider.LINK_TYPE_TYPENAME + name),
                       $(name));
      }
      else {
        add(name);
      }
    }
  }

  private abstract static class TypeNameVisitor implements TypeVisitor {
    private int myDepth = 0;
    private final static int MAX_DEPTH = 6;

    @Override
    public void oneOf(OneOf oneOf) {
      myDepth++;
      if (myDepth>MAX_DEPTH) {
        add("...");
        return;
      }
      add("Union[");
      processList(oneOf.oneOfTypes, ", ");
      add("]");
      myDepth--;
    }

    private void processList(Collection<TypeModel> list, String separator) {
      boolean first = true;
      for (TypeModel t : list) {
        if (!first) {
          add(separator);
        }
        else {
          first = false;
        }

        t.accept(this);
      }
    }

    protected abstract void add(String s);

    @Override
    public void collectionOf(CollectionOf collectionOf) {
      myDepth++;
      if (myDepth>MAX_DEPTH) {
        add("...");
        return;
      }
      final String name = collectionOf.collectionName;
      final String typingName = PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(name);
      addType(typingName != null ? typingName : name);
      add("[");
      processList(collectionOf.elementTypes, ", ");
      add("]");
      myDepth--;
    }

    protected abstract void addType(String name);

    @Override
    public void name(String name) {
      addType(name);
    }

    @Override
    public void function(FunctionType function) {
      myDepth++;
      if (myDepth>MAX_DEPTH) {
        add("...");
        return;
      }
      add("(");
      final Collection<TypeModel> parameters = function.parameters;
      if (parameters != null) {
        processList(parameters, ", ");
      }
      else {
        add("...");
      }
      add(") -> ");
      function.returnType.accept(this);
      myDepth--;
    }

    @Override
    public void param(ParamType param) {
      myDepth++;
      if (myDepth>MAX_DEPTH) {
        add("...");
        return;
      }
      if (param.name != null) {
        add(param.name);
      }
      if (param.type != null) {
        if (param.name != null) {
          add(": ");
        }
        param.type.accept(this);
      }
      myDepth--;
    }

    @Override
    public void unknown(UnknownType type) {
      type.type.accept(this);
    }

    @Override
    public void optional(OptionalType type) {
      add("Optional[");
      type.type.accept(this);
      add("]");
    }

    @Override
    public void tuple(TupleType type) {
      add("Tuple[");
      processList(type.members, ", ");
      if (type.homogeneous) {
        add(", ...");
      }
      add("]");
    }
  }
}
