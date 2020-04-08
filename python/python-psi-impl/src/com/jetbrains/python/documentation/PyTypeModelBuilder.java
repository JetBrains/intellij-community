// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.ChainIterable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.combUp;

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

    @NotNull
    public String asPep484TypeHint() {
      final TypeToStringVisitor visitor = new TypeToPep484TypeHintVisitor();
      accept(visitor);
      return visitor.getString();
    }
  }

  static class OneOf extends TypeModel {
    private final Collection<TypeModel> oneOfTypes;

    private OneOf(Collection<TypeModel> oneOfTypes) {
      this.oneOfTypes = oneOfTypes;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.oneOf(this);
    }
  }

  private static class CollectionOf extends TypeModel {
    private final TypeModel collectionType;
    private final List<TypeModel> elementTypes;

    private CollectionOf(TypeModel collectionType, List<TypeModel> elementTypes) {
      this.collectionType = collectionType;
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
    private final String name;

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

    TupleType(List<TypeModel> members, boolean homogeneous) {
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

    ClassObjectType(TypeModel classType) {
      this.classType = classType;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.classObject(this);
    }
  }

  static class GenericType extends TypeModel {
    private final String name;

    GenericType(@Nullable String name) {
      this.name = name;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.genericType(this);
    }
  }

  private static class OneOfLiterals extends TypeModel {

    @NotNull
    private final List<PyLiteralType> literals;

    private OneOfLiterals(@NotNull List<PyLiteralType> literals) {
      this.literals = literals;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.oneOfLiterals(this);
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
    if (type instanceof PyTypedDictType) {
      PyTypedDictType typedDictType = (PyTypedDictType)type;
      if (typedDictType.isInferred()) {
        return build(new PyCollectionTypeImpl(typedDictType.getPyClass(), false,
                                              typedDictType.getElementTypes()), allowUnions);
      }
      else {
        result = NamedType.nameOrAny(type);
      }
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
    else if (type instanceof PyNamedTupleType) {
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
      final PyCollectionType asCollection = (PyCollectionType)type;
      final List<TypeModel> elementModels = new ArrayList<>();
      for (PyType elementType : asCollection.getElementTypes()) {
        elementModels.add(build(elementType, true));
      }
      if (!elementModels.isEmpty()) {
        final TypeModel collectionType = build(new PyClassTypeImpl(asCollection.getPyClass(), asCollection.isDefinition()), false);
        result = new CollectionOf(collectionType, elementModels);
      }
    }
    else if (type instanceof PyUnionType && allowUnions) {
      final PyUnionType unionType = (PyUnionType)type;
      final Collection<PyType> unionMembers = unionType.getMembers();
      final Pair<List<PyLiteralType>, List<PyType>> literalsAndOthers = extractLiterals(unionType);
      final Ref<PyType> optionalType = getOptionalType(unionType);
      if (literalsAndOthers != null) {
        final OneOfLiterals oneOfLiterals = new OneOfLiterals(literalsAndOthers.first);

        if (!literalsAndOthers.second.isEmpty()) {
          final List<TypeModel> otherTypeModels = ContainerUtil.map(literalsAndOthers.second, t -> build(t, false));
          result = new OneOf(ContainerUtil.prepend(otherTypeModels, oneOfLiterals));
        }
        else {
          result = oneOfLiterals;
        }
      }
      else if (optionalType != null) {
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
    else if (type instanceof PyGenericType) {
      result = new GenericType(type.getName());
    }
    else if (type != null && type.isBuiltin() && PyNames.BUILTIN_PATH_LIKE.equals(type.getName())) {
      result = new NamedType(PyNames.PATH_LIKE);
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

  private static @Nullable Pair<@NotNull List<PyLiteralType>, @NotNull List<PyType>> extractLiterals(@NotNull PyUnionType type) {
    final Collection<PyType> members = type.getMembers();

    final List<PyLiteralType> literalTypes = ContainerUtil.filterIsInstance(members, PyLiteralType.class);
    if (literalTypes.size() < 2) return null;

    final List<PyType> otherTypes = ContainerUtil.filter(members, m -> !(m instanceof PyLiteralType));

    return Pair.create(literalTypes, otherTypes);
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

    void oneOfLiterals(OneOfLiterals literals);
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

  private static class TypeToPep484TypeHintVisitor extends TypeToStringVisitor {
    @Override
    protected boolean maxDepthExceeded() {
      return false;
    }

    @Override
    public void function(FunctionType function) {
      add("Callable[");
      final Collection<TypeModel> parameters = function.parameters;
      if (parameters != null) {
        add("[");
        processList(parameters);
        add("]");
      }
      else {
        add("...");
      }
      add(", ");
      function.returnType.accept(this);
      add("]");
    }

    @Override
    public void param(ParamType param) {
      if (param.type != null) {
        param.type.accept(this);
      }
      else {
        add("Any");
      }
    }

    @Override
    public void collectionOf(CollectionOf collectionOf) {
      typingGenericFormat(collectionOf);
    }
  }

  private static class TypeToBodyWithLinksVisitor extends TypeNameVisitor {
    private final ChainIterable<String> myBody;
    private final PsiElement myAnchor;

    TypeToBodyWithLinksVisitor(ChainIterable<String> body, PsiElement anchor) {
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
    private boolean switchBuiltinToTyping = false;

    @Override
    public void oneOf(OneOf oneOf) {
      myDepth++;
      if (maxDepthExceeded()) {
        add("...");
        return;
      }
      add("Union[");
      processList(oneOf.oneOfTypes);
      add("]");
      myDepth--;
    }

    protected void processList(@NotNull Collection<TypeModel> list) {
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
      if (maxDepthExceeded()) {
        add("...");
        return;
      }
      final boolean allTypeParamsAreAny = ContainerUtil.and(collectionOf.elementTypes, t -> t == NamedType.ANY);
      if (allTypeParamsAreAny) {
        collectionOf.collectionType.accept(this);
      }
      else {
        typingGenericFormat(collectionOf);
      }
      myDepth--;
    }

    protected void typingGenericFormat(CollectionOf collectionOf) {
      final boolean prevSwitchBuiltinToTyping = switchBuiltinToTyping;
      switchBuiltinToTyping = true;
      collectionOf.collectionType.accept(this);
      switchBuiltinToTyping = prevSwitchBuiltinToTyping;

      if (!collectionOf.elementTypes.isEmpty()) {
        add("[");
        processList(collectionOf.elementTypes);
        add("]");
      }
    }

    protected abstract void addType(String name);

    @Override
    public void name(String name) {
      addType(switchBuiltinToTyping ? PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.getOrDefault(name, name) : name);
    }

    @Override
    public void function(FunctionType function) {
      myDepth++;
      if (maxDepthExceeded()) {
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

    protected boolean maxDepthExceeded() {
      return myDepth > MAX_DEPTH;
    }

    @Override
    public void param(ParamType param) {
      myDepth++;
      if (maxDepthExceeded()) {
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
      add("Tuple");
      if (!type.members.isEmpty()) {
        add("[");
        processList(type.members);
        if (type.homogeneous) {
          add(", ...");
        }
        add("]");
      }
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

    @Override
    public void oneOfLiterals(OneOfLiterals literals) {
      add(
        StreamEx
          .of(literals.literals)
          .map(PyLiteralType::getExpression)
          .map(PyExpression::getText)
          .joining(", ", "Literal[", "]")
      );
    }
  }
}
