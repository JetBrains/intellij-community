// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.ChainIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    abstract void accept(@NotNull TypeVisitor visitor);

    @NotNull
    public String asString() {
      final TypeToStringVisitor visitor = new TypeToStringVisitor();
      accept(visitor);
      return visitor.getString();
    }

    public void toBodyWithLinks(@NotNull ChainIterable<String> body, @NotNull PsiElement anchor) {
      final TypeToBodyWithLinksVisitor visitor = new TypeToBodyWithLinksVisitor(body, anchor);
      accept(visitor);
    }

    @NotNull
    public String asDescription() {
      final TypeToDescriptionVisitor visitor = new TypeToDescriptionVisitor();
      accept(visitor);
      return visitor.getDescription();
    }
  }

  static class OneOf extends TypeModel {
    private Collection<TypeModel> oneOfTypes;

    private OneOf(Collection<TypeModel> oneOfTypes) {
      this.oneOfTypes = oneOfTypes;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
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
    void accept(@NotNull TypeVisitor visitor) {
      visitor.collectionOf(this);
    }
  }

  static class NamedType extends TypeModel {

    @NotNull
    private static final NamedType ANY = new NamedType(PyNames.UNKNOWN_TYPE);

    @Nullable
    private String name;

    private NamedType(@Nullable String name) {
      this.name = name;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.name(name);
    }

    @NotNull
    private static NamedType nameOrAny(@Nullable PyType type) {
      return type == null ? ANY : new NamedType(type.getName());
    }
  }

  static class UnknownType extends TypeModel {
    private final TypeModel type;

    private UnknownType(TypeModel type) {
      this.type = type;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.unknown(this);
    }
  }

  static class OptionalType extends TypeModel {
    private final TypeModel type;

    private OptionalType(TypeModel type) {
      this.type = type;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
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
    void accept(@NotNull TypeVisitor visitor) {
      visitor.tuple(this);
    }
  }

  static class FunctionType extends TypeModel {
    @NotNull private final TypeModel returnType;
    @Nullable private final Collection<TypeModel> parameters;

    private FunctionType(@Nullable TypeModel returnType, @Nullable Collection<TypeModel> parameters) {
      this.returnType = returnType != null ? returnType : NamedType.ANY;
      this.parameters = parameters;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
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
    void accept(@NotNull TypeVisitor visitor) {
      visitor.param(this);
    }
  }
  
  static class ClassObjectType extends TypeModel {
    private final TypeModel classType;

    public ClassObjectType(TypeModel classType) {
      this.classType = classType;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.classObject(this);
    }
  } 
  
  static class GenericType extends TypeModel {
    private final String name;

    public GenericType(@Nullable String name) {
      this.name = name;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.genericType(this);
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
      return NamedType.nameOrAny(type);
    }
    myVisited.put(type, null); //mark as evaluating

    TypeModel result = null;
    if (type instanceof PyNamedTupleType) {
      result = NamedType.nameOrAny(type);
    }
    else if (type instanceof PyTupleType) {
      final PyTupleType tupleType = (PyTupleType)type;

      final List<PyType> elementTypes = tupleType.isHomogeneous()
                                        ? Collections.singletonList(tupleType.getIteratedItemType())
                                        : tupleType.getElementTypes();

      final List<TypeModel> elementModels = ContainerUtil.map(elementTypes, elementType -> build(elementType, true));
      result = new TupleType(elementModels, tupleType.isHomogeneous());
    }
    else if (type instanceof PyCollectionType) {
      final String name = type.getName();
      final List<PyType> elementTypes = ((PyCollectionType)type).getElementTypes();
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
      final Collection<PyType> unionMembers = unionType.getMembers();
      final Ref<PyType> optionalType = getOptionalType(unionType);
      if (optionalType != null) {
        result = new OptionalType(build(optionalType.get(), true));
      }
      else if (type instanceof PyDynamicallyEvaluatedType || PyTypeChecker.isUnknown(type, false, myContext)) {
        result = new UnknownType(build(unionType.excludeNull(myContext), true));
      }
      else if (unionMembers.stream().allMatch(t -> t instanceof PyClassType && ((PyClassType)t).isDefinition())) {
        final List<TypeModel> instanceTypes = ContainerUtil.map(unionMembers, t -> build(((PyClassType)t).toInstance(), allowUnions));
        result = new ClassObjectType(new OneOf(instanceTypes));
      }
      else {
        result = new OneOf(Collections2.transform(unionMembers, t -> build(t, false)));
      }
    }
    else if (type instanceof PyCallableType && !(type instanceof PyClassLikeType)) {
      result = buildCallable((PyCallableType)type);
    }
    else if (type instanceof PyInstantiableType && ((PyInstantiableType)type).isDefinition()) {
      final PyInstantiableType instanceType = ((PyInstantiableType)type).toInstance();
      // Special case: render Type[type] as just type
      if (type instanceof PyClassType && instanceType.equals(PyBuiltinCache.getInstance(((PyClassType)type).getPyClass()).getTypeType())) {
        result = NamedType.nameOrAny(type);
      }
      else {
        result = new ClassObjectType(build(instanceType, allowUnions));
      }
    }
    else if (type instanceof PyGenericType) {
      result = new GenericType(type.getName());
    }
    if (result == null) {
      result = NamedType.nameOrAny(type);
    }
    myVisited.put(type, result);
    return result;
  }

  @Nullable
  private static Ref<PyType> getOptionalType(@NotNull PyUnionType type) {
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
        return Ref.create(optional);
      }
    }
    return null;
  }

  private TypeModel buildCallable(@NotNull PyCallableType type) {
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

    void classObject(ClassObjectType type);

    void genericType(GenericType type);
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
      final TypeEvalContext context = TypeEvalContext.userInitiated(myAnchor.getProject(), myAnchor.getContainingFile());
      myBody.addItem(PyDocumentationLink.toPossibleClass(name, myAnchor, context));
    }
  }

  private static class TypeToDescriptionVisitor extends TypeNameVisitor {

    @NotNull
    private final StringBuilder myResult = new StringBuilder();

    @Override
    protected void add(String s) {
      myResult.append(s);
    }

    @Override
    protected void addType(String name) {
      add(name);
    }

    @NotNull
    public String getDescription() {
      return myResult.toString();
    }
  }

  private abstract static class TypeNameVisitor implements TypeVisitor {
    private int myDepth = 0;
    private final static int MAX_DEPTH = 6;

    @Override
    public void oneOf(OneOf oneOf) {
      myDepth++;
      if (myDepth > MAX_DEPTH) {
        add("...");
        return;
      }
      add("Union[");
      processList(oneOf.oneOfTypes);
      add("]");
      myDepth--;
    }

    private void processList(@NotNull Collection<TypeModel> list) {
      boolean first = true;
      for (TypeModel t : list) {
        if (!first) {
          add(", ");
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
      if (myDepth > MAX_DEPTH) {
        add("...");
        return;
      }
      final String name = collectionOf.collectionName;
      final String typingName = PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(name);
      addType(typingName != null ? typingName : name);
      add("[");
      processList(collectionOf.elementTypes);
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
      if (myDepth > MAX_DEPTH) {
        add("...");
        return;
      }
      add("(");
      final Collection<TypeModel> parameters = function.parameters;
      if (parameters != null) {
        processList(parameters);
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
      if (myDepth > MAX_DEPTH) {
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
      processList(type.members);
      if (type.homogeneous) {
        add(", ...");
      }
      add("]");
    }

    @Override
    public void classObject(ClassObjectType type) {
      add("Type[");
      type.classType.accept(this);
      add("]");
    }

    @Override
    public void genericType(GenericType type) {
      add(type.name);
    }
  }
}
