// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.highlightExpressionText;
import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.styledSpan;

public class PyTypeModelBuilder {
  private final Map<PyType, TypeModel> myVisited = Maps.newHashMap();
  private final TypeEvalContext myContext;

  PyTypeModelBuilder(TypeEvalContext context) {
    this.myContext = context;
  }

  abstract static class TypeModel {
    abstract void accept(@NotNull TypeVisitor visitor);

    public @NotNull String asString() {
      final TypeToStringVisitor visitor = new TypeToStringVisitor();
      accept(visitor);
      return visitor.getString();
    }

    public void toBodyWithLinks(@NotNull HtmlBuilder body, @NotNull PsiElement anchor) {
      final TypeToBodyWithLinksVisitor visitor = new TypeToBodyWithLinksVisitor(body, anchor);
      accept(visitor);
    }

    public @NotNull String asDescription() {
      final TypeToDescriptionVisitor visitor = new TypeToDescriptionVisitor();
      accept(visitor);
      return visitor.getDescription();
    }

    public @NotNull String asPep484TypeHint() {
      final TypeToStringVisitor visitor = new TypeToPep484TypeHintVisitor();
      accept(visitor);
      return visitor.getString();
    }

    public @NotNull String asStringWithAdditionalInfo() {
      TypeToStringVisitor visitor = new VerboseTypeInfoVisitor();
      accept(visitor);
      return visitor.getString();
    }
  }

  static final class OneOf extends TypeModel {
    private final Collection<TypeModel> oneOfTypes;
    private final boolean bitwiseOrUnionAllowed;

    private OneOf(Collection<TypeModel> oneOfTypes, boolean bitwiseOrUnionAllowed) {
      this.oneOfTypes = oneOfTypes;
      this.bitwiseOrUnionAllowed = bitwiseOrUnionAllowed;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.oneOf(this);
    }
  }

  private static final class NarrowedType extends TypeModel {
    private final TypeModel narrowedType;
    private final boolean isTypeIs;

    private NarrowedType(@NotNull TypeModel narrowedType, boolean isTypeIs) {
      this.narrowedType = narrowedType;
      this.isTypeIs = isTypeIs;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.narrowedType(this);
    }
  }
  

  private static final class CollectionOf extends TypeModel {
    private final TypeModel collectionType;
    private final List<TypeModel> elementTypes;
    private final boolean useTypingAlias;

    private CollectionOf(TypeModel collectionType,
                         List<TypeModel> elementTypes,
                         boolean useTypingAlias) {
      this.collectionType = collectionType;
      this.elementTypes = elementTypes;
      this.useTypingAlias = useTypingAlias;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.collectionOf(this);
    }
  }

  static final class NamedType extends TypeModel {

    private static final @NotNull NamedType ANY = new NamedType(PyNames.UNKNOWN_TYPE);

    private final @Nullable @NlsSafe String name;

    private NamedType(@Nullable String name) {
      this.name = name;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.name(name);
    }

    private static @NotNull NamedType nameOrAny(@Nullable PyType type) {
      return type == null ? ANY : new NamedType(type.getName());
    }
  }

  static final class UnknownType extends TypeModel {
    private final TypeModel type;
    private final boolean bitwiseOrUnionAllowed;

    private UnknownType(TypeModel type, boolean bitwiseOrUnionAllowed) {
      this.type = type;
      this.bitwiseOrUnionAllowed = bitwiseOrUnionAllowed;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.unknown(this);
    }
  }

  static final class OptionalType extends TypeModel {
    private final TypeModel type;
    private final boolean bitwiseOrUnionAllowed;

    private OptionalType(TypeModel type, boolean bitwiseOrUnionAllowed) {
      this.type = type;
      this.bitwiseOrUnionAllowed = bitwiseOrUnionAllowed;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.optional(this);
    }
  }

  static class TupleType extends TypeModel {
    private final List<TypeModel> members;
    private final boolean homogeneous;
    private final boolean useTypingAlias;

    TupleType(List<TypeModel> members, boolean homogeneous, boolean useTypingAlias) {
      this.members = members;
      this.homogeneous = homogeneous;
      this.useTypingAlias = useTypingAlias;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.tuple(this);
    }
  }

  static final class FunctionType extends TypeModel {
    private final @NotNull TypeModel returnType;
    private final @Nullable Collection<TypeModel> parameters;

    private FunctionType(@Nullable TypeModel returnType, @Nullable Collection<TypeModel> parameters) {
      this.returnType = returnType != null ? returnType : NamedType.ANY;
      this.parameters = parameters;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.function(this);
    }
  }

  static final class ParamType extends TypeModel {
    private final @Nullable @NlsSafe String name;
    private final @Nullable TypeModel type;

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
    private final boolean useTypingAlias;

    ClassObjectType(TypeModel classType, boolean useTypingAlias) {
      this.classType = classType;
      this.useTypingAlias = useTypingAlias;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.classObject(this);
    }
  }

  static class TypeVarType extends TypeModel {
    private final @NlsSafe String name;
    private final TypeModel bound;

    TypeVarType(@Nullable String name, @Nullable TypeModel bound) {
      this.name = name;
      this.bound = bound;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.typeVarType(this);
    }
  }

  private static final class OneOfLiterals extends TypeModel {

    private final @NotNull List<PyLiteralType> literals;

    private OneOfLiterals(@NotNull List<PyLiteralType> literals) {
      this.literals = literals;
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.oneOfLiterals(this);
    }
  }
  
  private static class CallableParameterList extends TypeModel {
    private final List<TypeModel> parameters;

    private CallableParameterList(@NotNull List<TypeModel> parameters) {
      this.parameters = parameters; 
    }

    @Override
    void accept(@NotNull TypeVisitor visitor) {
      visitor.callableParameterList(this);
    }
  }

  /**
   * Builds tree-like type model for PyType
   */
  @SuppressWarnings("rawtypes")
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
      result = NamedType.nameOrAny(type);
    }
    else if (type instanceof PyInstantiableType && ((PyInstantiableType<?>)type).isDefinition()) {
      final PyInstantiableType instanceType = ((PyInstantiableType<?>)type).toInstance();
      // Special case: render Type[type] as just type
      if (type instanceof PyClassType && instanceType.equals(PyBuiltinCache.getInstance(((PyClassType)type).getPyClass()).getTypeType())) {
        result = NamedType.nameOrAny(type);
      }
      else {
        boolean useTypingAlias = PythonLanguageLevelPusher
          .getLanguageLevelForFile(myContext.getOrigin().getOriginalFile())
          .isOlderThan(LanguageLevel.PYTHON39);

        result = new ClassObjectType(build(instanceType, allowUnions), useTypingAlias);
      }
    }
    else if (type instanceof PyNamedTupleType) {
      result = NamedType.nameOrAny(type);
    }
    else if (type instanceof PyTupleType tupleType) {

      final List<PyType> elementTypes = tupleType.isHomogeneous()
                                        ? Collections.singletonList(tupleType.getIteratedItemType())
                                        : tupleType.getElementTypes();

      boolean useTypingAlias =
        PythonLanguageLevelPusher.getLanguageLevelForFile(tupleType.getPyClass().getContainingFile()).isOlderThan(LanguageLevel.PYTHON39);
      final List<TypeModel> elementModels = ContainerUtil.map(elementTypes, elementType -> build(elementType, true));
      result = new TupleType(elementModels, tupleType.isHomogeneous(), useTypingAlias);
    }
    else if (type instanceof PyCollectionType asCollection) {
      final List<TypeModel> elementModels = new ArrayList<>();
      for (PyType elementType : asCollection.getElementTypes()) {
        elementModels.add(build(elementType, true));
      }
      if (!elementModels.isEmpty()) {
        PyClass pyClass = asCollection.getPyClass();
        final TypeModel collectionType = build(new PyClassTypeImpl(pyClass, asCollection.isDefinition()), false);
        boolean useTypingAlias =
          PythonLanguageLevelPusher.getLanguageLevelForFile(pyClass.getContainingFile()).isOlderThan(LanguageLevel.PYTHON39);
        result = new CollectionOf(collectionType, elementModels, useTypingAlias);
      }
    }
    else if (type instanceof PyNarrowedType narrowedType) {
      result = new NarrowedType(build(narrowedType.getNarrowedType(), true), narrowedType.getTypeIs());
    }
    else if (type instanceof PyUnionType unionType && allowUnions) {
      final Collection<PyType> unionMembers = unionType.getMembers();
      final Pair<List<PyLiteralType>, List<PyType>> literalsAndOthers = extractLiterals(unionType);
      final Ref<PyType> optionalType = getOptionalType(unionType);
      if (literalsAndOthers != null) {
        final OneOfLiterals oneOfLiterals = new OneOfLiterals(literalsAndOthers.first);

        if (!literalsAndOthers.second.isEmpty()) {
          final List<TypeModel> otherTypeModels = ContainerUtil.map(literalsAndOthers.second, t -> build(t, false));
          result = new OneOf(ContainerUtil.prepend(otherTypeModels, oneOfLiterals),
                             PyTypingTypeProvider.isBitwiseOrUnionAvailable(myContext));
        }
        else {
          result = oneOfLiterals;
        }
      }
      else if (optionalType != null) {
        result = new OptionalType(build(optionalType.get(), true), PyTypingTypeProvider.isBitwiseOrUnionAvailable(myContext));
      }
      else if (type instanceof PyDynamicallyEvaluatedType || PyTypeChecker.isUnknown(type, false, myContext)) {
        result = new UnknownType(build(unionType.excludeNull(), true), PyTypingTypeProvider.isBitwiseOrUnionAvailable(myContext));
      }
      else if (ContainerUtil.all(unionMembers, t -> t instanceof PyClassType && ((PyClassType)t).isDefinition())) {
        final List<TypeModel> instanceTypes = ContainerUtil.map(unionMembers, t -> build(((PyClassType)t).toInstance(), allowUnions));

        final var useTypingAlias = LanguageLevel.forElement(myContext.getOrigin().getOriginalFile()).isOlderThan(LanguageLevel.PYTHON39);

        result = new ClassObjectType(new OneOf(instanceTypes, PyTypingTypeProvider.isBitwiseOrUnionAvailable(myContext)), useTypingAlias);
      }
      else {
        result = new OneOf(Collections2.transform(unionMembers, t -> build(t, false)),
                           PyTypingTypeProvider.isBitwiseOrUnionAvailable(myContext));
      }
    }
    else if (type instanceof PyCallableType && !(type instanceof PyClassLikeType)) {
      result = buildCallable((PyCallableType)type);
    }
    else if (type instanceof PyTypeVarType typeVarType) {
      PyType effectiveBound = PyTypeUtil.getEffectiveBound(typeVarType);
      result = new TypeVarType(type.getName(), effectiveBound != null ? build(effectiveBound, true) : null);
    }
    else if (type instanceof PyCallableParameterListType callableParameterListType) {
      result = new CallableParameterList(buildParameterModels(callableParameterListType.getParameters()));
    }
    if (result == null) {
      result = NamedType.nameOrAny(type);
    }
    myVisited.put(type, result);
    return result;
  }

  private static @Nullable Ref<PyType> getOptionalType(@NotNull PyUnionType type) {
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
    final List<PyCallableParameter> parameters = type.getParameters(myContext);
    List<TypeModel> parameterModels = null;
    if (parameters != null) {
      parameterModels = buildParameterModels(parameters);
    }
    final PyType ret = type.getReturnType(myContext);
    final TypeModel returnType = build(ret, true);
    return new FunctionType(returnType, parameterModels);
  }

  private @NotNull List<TypeModel> buildParameterModels(@NotNull List<PyCallableParameter> parameters) {
    List<TypeModel> parameterModels = new ArrayList<>();
    for (PyCallableParameter parameter : parameters) {
      final var paramType = parameter.getType(myContext);
      if (paramType instanceof PyParamSpecType || paramType instanceof PyConcatenateType) {
        parameterModels.add(new ParamType(null, build(parameter.getType(myContext), true)));
      }
      else {
        parameterModels.add(new ParamType(parameter.getName(), build(parameter.getType(myContext), true)));
      }
    }
    return parameterModels;
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

    void typeVarType(TypeVarType type);

    void oneOfLiterals(OneOfLiterals literals);

    void narrowedType(NarrowedType type);

    void callableParameterList(CallableParameterList list);
  }

  private static class TypeToStringVisitor extends TypeNameVisitor {
    @Override
    protected @NotNull HtmlChunk styled(@Nls String text, @NotNull TextAttributesKey style) {
      return HtmlChunk.raw(StringUtil.notNullize(text));
    }

    @Override
    protected @NotNull HtmlChunk escaped(@Nls String text) {
      return HtmlChunk.raw(StringUtil.notNullize(text));
    }

    @Override
    protected @NotNull HtmlChunk className(@Nls String name) {
      return escaped(name);
    }

    @Override
    protected @NotNull HtmlChunk styledExpression(@Nls String expressionText, @NotNull PyExpression expression) {
      return HtmlChunk.raw(StringUtil.notNullize(expressionText));
    }

    public String getString() {
      return myBody.toString();
    }

    @Override
    public void unknown(UnknownType type) {
      final TypeModel nested = type.type;
      if (nested != null) {
        if (type.bitwiseOrUnionAllowed) {
          nested.accept(this);
          add(HtmlChunk.raw(" | " + PyNames.UNKNOWN_TYPE));
        }
        else {
          add(HtmlChunk.raw("Union[")); // NON-NLS
          nested.accept(this);
          add(HtmlChunk.raw(", " + PyNames.UNKNOWN_TYPE + "]"));
        }
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
      add(HtmlChunk.raw("Callable["));  //NON-NLS
      final Collection<TypeModel> parameters = function.parameters;
      if (parameters != null) {
        add(HtmlChunk.raw("["));
        processList(parameters);
        add(HtmlChunk.raw("]"));
      }
      else {
        add(HtmlChunk.raw("..."));
      }
      add(HtmlChunk.raw(", "));
      function.returnType.accept(this);
      add(HtmlChunk.raw("]"));
    }

    @Override
    public void param(ParamType param) {
      if (param.type != null) {
        param.type.accept(this);
      }
      else {
        add(HtmlChunk.raw(PyNames.UNKNOWN_TYPE));
      }
    }

    @Override
    public void collectionOf(CollectionOf collectionOf) {
      typingGenericFormat(collectionOf);
    }
  }

  private static class TypeToBodyWithLinksVisitor extends TypeNameVisitor {
    private final PsiElement myAnchor;

    TypeToBodyWithLinksVisitor(HtmlBuilder body, PsiElement anchor) {
      myBody = body;
      myAnchor = anchor;
    }

    @Override
    protected @NotNull HtmlChunk styled(@Nls String text, @NotNull TextAttributesKey style) {
      return styledSpan(text, style);
    }

    @Override
    protected @NotNull HtmlChunk escaped(@Nls String text) {
      return HtmlChunk.text(text);
    }

    @Override
    protected @NotNull HtmlChunk className(@Nls String name) {
      final TypeEvalContext context = TypeEvalContext.userInitiated(myAnchor.getProject(), myAnchor.getContainingFile());
      return PyDocumentationLink.toPossibleClass(name, myAnchor, context);
    }

    @Override
    protected @NotNull HtmlChunk styledExpression(@Nls String expressionText, @NotNull PyExpression expression) {
      return highlightExpressionText(expressionText, expression);
    }
  }

  private static class TypeToDescriptionVisitor extends TypeNameVisitor {
    @Override
    protected @NotNull HtmlChunk styled(@Nls String text, @NotNull TextAttributesKey style) {
      return HtmlChunk.raw(StringUtil.notNullize(text));
    }

    @Override
    protected @NotNull HtmlChunk escaped(@Nls String text) {
      return HtmlChunk.raw(StringUtil.notNullize(text));
    }

    @Override
    protected @NotNull HtmlChunk className(@Nls String name) {
      return escaped(name);
    }

    @Override
    protected @NotNull HtmlChunk styledExpression(@Nls String expressionText, @NotNull PyExpression expression) {
      return HtmlChunk.raw(StringUtil.notNullize(expressionText));
    }

    public @NotNull String getDescription() {
      return myBody.toString();
    }
  }

  private abstract static class TypeNameVisitor implements TypeVisitor {
    private int myDepth = 0;
    private static final int MAX_DEPTH = 6;
    private boolean switchBuiltinToTyping = false;
    protected HtmlBuilder myBody = new HtmlBuilder();

    @Override
    public void oneOf(OneOf oneOf) {
      myDepth++;
      if (maxDepthExceeded()) {
        add(styled("...", PyHighlighter.PY_DOT));
        return;
      }
      if (oneOf.bitwiseOrUnionAllowed) {
        processList(oneOf.oneOfTypes, " | ");
      }
      else {
        add(escaped("Union")); //NON-NLS
        add(styled("[", PyHighlighter.PY_BRACKETS));
        processList(oneOf.oneOfTypes);
        add(styled("]", PyHighlighter.PY_BRACKETS));
      }
      myDepth--;
    }

    protected void processList(@NotNull Collection<TypeModel> list) {
      processList(list, ", ");
    }

    protected void processList(@NotNull Collection<TypeModel> list, @NotNull @Nls String separator) {
      boolean first = true;
      for (TypeModel t : list) {
        if (!first) {
          if (separator.equals(", ")) {
            add(styled(separator, PyHighlighter.PY_COMMA));
          }
          else if (separator.equals(" | ")) {
            add(styled(separator, PyHighlighter.PY_OPERATION_SIGN));
          }
          else {
            add(escaped(separator));
          }
        }
        else {
          first = false;
        }

        t.accept(this);
      }
    }

    protected void add(@NotNull HtmlChunk chunk) {
      myBody.append(chunk);
    }

    protected abstract @NotNull HtmlChunk styled(@Nls String text, @NotNull TextAttributesKey style);

    protected abstract @NotNull HtmlChunk escaped(@Nls String text);

    protected abstract @NotNull HtmlChunk className(@Nls String name);

    protected abstract @NotNull HtmlChunk styledExpression(@Nls String expressionText, @NotNull PyExpression expression);

    @Override
    public void collectionOf(CollectionOf collectionOf) {
      myDepth++;
      if (maxDepthExceeded()) {
        add(styled("...", PyHighlighter.PY_DOT));
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
      switchBuiltinToTyping = collectionOf.useTypingAlias;
      collectionOf.collectionType.accept(this);
      switchBuiltinToTyping = prevSwitchBuiltinToTyping;

      if (!collectionOf.elementTypes.isEmpty()) {
        add(styled("[", PyHighlighter.PY_BRACKETS));
        processList(collectionOf.elementTypes);
        add(styled("]", PyHighlighter.PY_BRACKETS));
      }
    }

    @Override
    public void narrowedType(@NotNull NarrowedType narrowedType) {
      if (narrowedType.isTypeIs) {
        add(styled("TypeIs", PyHighlighter.PY_CLASS_DEFINITION));
      }
      else {
        add(styled("TypeGuard", PyHighlighter.PY_CLASS_DEFINITION));
      }
      add(styled("[", PyHighlighter.PY_BRACKETS));
      narrowedType.narrowedType.accept(this);
      add(styled("]", PyHighlighter.PY_BRACKETS));
    }

    @Override
    public void name(@NlsSafe String name) {
      add(className(switchBuiltinToTyping ? PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.getOrDefault(name, name) : name));
    }

    @Override
    public void function(FunctionType function) {
      myDepth++;
      if (maxDepthExceeded()) {
        add(styled("...", PyHighlighter.PY_DOT));
        return;
      }
      add(styled("(", PyHighlighter.PY_PARENTHS));
      final Collection<TypeModel> parameters = function.parameters;
      if (parameters != null) {
        processList(parameters);
      }
      else {
        add(styled("...", PyHighlighter.PY_DOT));
      }
      add(styled(")", PyHighlighter.PY_PARENTHS));
      add(escaped(" -> "));
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
        add(styled("...", PyHighlighter.PY_DOT));
        return;
      }
      if (param.name != null) {
        add(styled(param.name, PyHighlighter.PY_PARAMETER));
      }
      if (param.type != null) {
        if (param.name != null) {
          add(styled(": ", PyHighlighter.PY_OPERATION_SIGN));
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
      if (type.bitwiseOrUnionAllowed) {
        type.type.accept(this);
        add(styled(" | ", PyHighlighter.PY_OPERATION_SIGN));
        add(styled("None", PyHighlighter.PY_KEYWORD)); //NON-NLS
      }
      else {
        add(escaped("Optional")); //NON-NLS
        add(styled("[", PyHighlighter.PY_BRACKETS));
        type.type.accept(this);
        add(styled("]", PyHighlighter.PY_BRACKETS));
      }
    }

    @Override
    public void tuple(TupleType type) {
      if (type.useTypingAlias) {
        add(escaped("Tuple")); //NON-NLS
      }
      else {
        add(styled("tuple", PyHighlighter.PY_BUILTIN_NAME)); //NON-NLS
      }
      add(styled("[", PyHighlighter.PY_BRACKETS));
      if (!type.members.isEmpty()) {
        processList(type.members);
        if (type.homogeneous) {
          add(styled(", ", PyHighlighter.PY_COMMA));
          add(styled("...", PyHighlighter.PY_DOT));
        }
      }
      else {
        add(styled("()", PyHighlighter.PY_PARENTHS));
      }
      add(styled("]", PyHighlighter.PY_BRACKETS));
    }

    @Override
    public void classObject(ClassObjectType type) {
      if (type.useTypingAlias) {
        add(escaped("Type")); //NON-NLS
      }
      else {
        add(styled("type", PyHighlighter.PY_BUILTIN_NAME)); //NON-NLS
      }
      add(styled("[", PyHighlighter.PY_BRACKETS));
      type.classType.accept(this);
      add(styled("]", PyHighlighter.PY_BRACKETS));
    }

    @Override
    public void typeVarType(TypeVarType type) {
      if (type.name != null) {
        add(escaped(type.name));
      }
    }

    @Override
    public void oneOfLiterals(OneOfLiterals literals) {
      add(
        new HtmlBuilder()
          .append(escaped("Literal")) //NON-NLS
          .append(styled("[", PyHighlighter.PY_BRACKETS))
          .append(StreamEx
                    .of(literals.literals)
                    .map(PyLiteralType::getExpression)
                    .map(expr -> styledExpression(expr.getText(), expr))
                    .collect(HtmlChunk.toFragment(styled(", ", PyHighlighter.PY_COMMA))))
          .append(styled("]", PyHighlighter.PY_BRACKETS))
          .toFragment());
    }

    @Override
    public void callableParameterList(CallableParameterList list) {
      add(HtmlChunk.raw("["));
      processList(list.parameters);
      add(HtmlChunk.raw("]"));
    }
  }

  private static class VerboseTypeInfoVisitor extends TypeToStringVisitor {

    @Override
    public void typeVarType(TypeVarType type) {
      if (type.name != null) {
        add(escaped(type.name));
        if (type.bound != null) {
          add(escaped(" ≤: "));
          type.bound.accept(this);
        }
      }
    }
  }
}
