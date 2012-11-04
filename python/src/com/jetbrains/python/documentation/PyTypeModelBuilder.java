package com.jetbrains.python.documentation;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.ChainIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.$;
import static com.jetbrains.python.documentation.DocumentationBuilderKit.combUp;

/**
 * @author traff
 */
public class PyTypeModelBuilder {
  @NonNls static final String UNKNOWN = "unknown";
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
    private TypeModel elementType;

    private CollectionOf(String collectionName, TypeModel elementType) {
      this.collectionName = collectionName;
      this.elementType = elementType;
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

  private static TypeModel _(String name) {
    return new NamedType(name);
  }

  static class FunctionType extends TypeModel {
    private TypeModel returnType;
    private Collection<TypeModel> parameters;

    FunctionType(@NotNull TypeModel returnType, Collection<TypeModel> parameters) {
      this.returnType = returnType;
      this.parameters = parameters;
    }

    @Override
    void accept(TypeVisitor visitor) {
      visitor.function(this);
    }
  }

  static class ParamType extends TypeModel {
    private final String name;
    private final TypeModel type;


    private ParamType(String name, @Nullable TypeModel type) {
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
      return type != null ? _(type.getName()) : _(UNKNOWN);
    }
    myVisited.put(type, null); //mark as evaluating

    TypeModel result = null;
    if (type instanceof PyTypeReference) {
      final PyType resolved = ((PyTypeReference)type).resolve(null, myContext);
      if (resolved != null) {
        result = build(resolved, true);
      }
    }
    else if (type instanceof PyCollectionType) {
      final String name = type.getName();
      final PyType elementType = ((PyCollectionType)type).getElementType(myContext);
      if (elementType != null) {
        result = new CollectionOf(name, build(elementType, true));
      }
    }
    else if (type instanceof PyUnionType && allowUnions) {
      if (type instanceof PyDynamicallyEvaluatedType) {
        result = build(((PyDynamicallyEvaluatedType)type).exclude(null, myContext), true);
      }
      else {
        result = new OneOf(
          Collections2.transform(((PyUnionType)type).getMembers(), new Function<PyType, TypeModel>() {
            @Override
            public TypeModel apply(PyType t) {
              return build(t, false);
            }
          }));
      }
    }
    if (result == null) {
      result = type != null ? _(type.getName()) : _(UNKNOWN);
    }
    myVisited.put(type, result);
    return result;
  }


  public TypeModel build(PyFunction function) {
    final PyType returnType = function.getReturnType(myContext, null);
    return new FunctionType(build(returnType, true), Collections2.transform(Lists.newArrayList(function.getParameterList().getParameters()),
                                                                            new Function<PyParameter, TypeModel>() {
                                                                              @Override
                                                                              public TypeModel apply(PyParameter p) {
                                                                                final PyNamedParameter np = p.getAsNamed();
                                                                                if (np != null) {
                                                                                  TypeModel paramType =
                                                                                    _(UNKNOWN);
                                                                                  final PyType t = np.getType(myContext);
                                                                                  if (t != null) {
                                                                                    paramType = build(t, true);
                                                                                  }
                                                                                  return new ParamType(np.getName(), paramType);
                                                                                }
                                                                                return new ParamType(p.toString(), null);
                                                                              }
                                                                            }));
  }

  private interface TypeVisitor {
    void oneOf(OneOf oneOf);

    void collectionOf(CollectionOf collectionOf);

    void name(String name);

    void function(FunctionType type);

    void param(ParamType text);
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
    @Override
    public void oneOf(OneOf oneOf) {
      add("one of (");
      processListCommaSeparated(oneOf.oneOfTypes);
      add(")");
    }

    private void processListCommaSeparated(Collection<TypeModel> list) {
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
      addType(collectionOf.collectionName);
      add(" of ");
      collectionOf.elementType.accept(this);
    }

    protected abstract void addType(String name);

    @Override
    public void name(String name) {
      addType(name);
    }

    @Override
    public void function(FunctionType function) {
      add("(");
      processListCommaSeparated(function.parameters);
      add(") -> ");
      function.returnType.accept(this);
      add("\n");
    }

    @Override
    public void param(ParamType param) {
      add(param.name);
      if (param.type != null) {
        add(": ");
        param.type.accept(this);
      }
    }
  }
}
