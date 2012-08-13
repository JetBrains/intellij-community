package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyClassTypeImpl extends UserDataHolderBase implements PyClassType {

  @NotNull protected final PyClass myClass;
  protected final boolean myIsDefinition;

  private static ThreadLocal<Set<Pair<PyClass, String>>> ourResolveMemberStack = new ThreadLocal<Set<Pair<PyClass, String>>>() {
    @Override
    protected Set<Pair<PyClass, String>> initialValue() {
      return new HashSet<Pair<PyClass, String>>();
    }
  };

  /**
   * Describes a class-based type. Since everything in Python is an instance of some class, this type pretty much completes
   * the type system :)
   * Note that classes' and instances' member list can change during execution, so it is important to construct an instance of PyClassType
   * right in the place of reference, so that such changes could possibly be accounted for.
   *
   * @param source        PyClass which defines this type. For builtin or external classes, skeleton files contain the definitions.
   * @param isDefinition whether this type describes an instance or a definition of the class.
   */
  public PyClassTypeImpl(@NotNull PyClass source, boolean isDefinition) {
    PyClass originalElement = CompletionUtil.getOriginalElement(source);
    myClass = originalElement != null ? originalElement : source;
    myIsDefinition = isDefinition;
  }

  public <T> PyClassTypeImpl withUserData(Key<T> key, T value) {
    putUserData(key, value);
    return this;
  }

  /**
   * @return a PyClass which defined this type.
   */
  @Override
  @NotNull
  public PyClass getPyClass() {
    return myClass;
  }

  /**
   * @return whether this type refers to an instance or a definition of the class.
   */
  @Override public boolean isDefinition() {
    return myIsDefinition;
  }

  @Override public PyClassType toInstance() {
    return myIsDefinition ? new PyClassTypeImpl(myClass, false) : this;
  }

  @Override@Nullable
  public String getClassQName() {
    return myClass.getQualifiedName();
  }

  @Nullable
  public List<? extends RatedResolveResult> resolveMember(final String name, @Nullable PyExpression location, AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    final Set<Pair<PyClass, String>> resolving = ourResolveMemberStack.get();
    final Pair<PyClass, String> key = Pair.create(myClass, name);
    if (resolving.contains(key)) {
      return Collections.emptyList();
    }
    resolving.add(key);
    try {
      return doResolveMember(name, location, direction, resolveContext);
    }
    finally {
      resolving.remove(key);
    }
  }

  @Nullable
  private List<? extends RatedResolveResult> doResolveMember(@NotNull String name,
                                                             @Nullable PyExpression location,
                                                             @NotNull AccessDirection direction,
                                                             @NotNull PyResolveContext resolveContext) {
    if (myClass == null) {
      return null;
    }
    if (resolveContext.allowProperties()) {
      Property property = myClass.findProperty(name);
      if (property != null) {
        Maybe<Callable> accessor = property.getByDirection(direction);
        if (accessor.isDefined()) {
          Callable accessor_code = accessor.value();
          ResolveResultList ret = new ResolveResultList();
          if (accessor_code != null) ret.poke(accessor_code, RatedResolveResult.RATE_NORMAL);
          PyTargetExpression site = property.getDefinitionSite();
          if (site != null) ret.poke(site, RatedResolveResult.RATE_LOW);
          if (ret.size() > 0) {
            return ret;
          }
          else {
            return null;
          } // property is found, but the required accessor is explicitly absent
        }
      }
    }

    if ("super".equals(getClassQName()) && isBuiltin(resolveContext.getTypeEvalContext()) && location instanceof PyCallExpression) {
      // methods of super() call are not of class super!
      PyExpression first_arg = ((PyCallExpression)location).getArgument(0, PyExpression.class);
      if (first_arg != null) { // the usual case: first arg is the derived class that super() is proxying for
        PyType first_arg_type = first_arg.getType(resolveContext.getTypeEvalContext());
        if (first_arg_type instanceof PyClassType) {
          PyClass derived_class = ((PyClassType)first_arg_type).getPyClass();
          if (derived_class != null) {
            final Iterator<PyClass> base_it = derived_class.iterateAncestorClasses().iterator();
            if (base_it.hasNext()) {
              return new PyClassTypeImpl(base_it.next(), true).resolveMember(name, location, direction, resolveContext);
            }
            else {
              return null; // no base classes = super() cannot proxy anything meaningful from a base class
            }
          }
        }
      }
    }

    PsiElement classMember = resolveClassMember(myClass, myIsDefinition, name, location);
    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    for (PyClassRef superClass : myClass.iterateAncestors()) {
      final PyClass pyClass = superClass.getPyClass();
      if (pyClass != null) {
        PsiElement superMember = resolveClassMember(pyClass, myIsDefinition, name, null);
        if (superMember != null) {
          return ResolveResultList.to(superMember);
        }
      }
      else {
        final PsiElement element = superClass.getElement();
        if (element != null) {
          for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
            final PyType refType = typeProvider.getReferenceType(element, resolveContext.getTypeEvalContext(), myClass);
            if (refType != null) {
              return refType.resolveMember(name, location, direction, resolveContext);
            }
          }
        }
      }
    }
    if (isDefinition() && myClass.isNewStyleClass()) {
      PyClassType typeType = getMetaclassType();
      if (typeType != null) {
        List<? extends RatedResolveResult> typeMembers = typeType.resolveMember(name, location, direction, resolveContext);
        if (typeMembers != null && !typeMembers.isEmpty()) {
          return typeMembers;
        }
      }
    }

    classMember = resolveByMembersProviders(this, name);  //ask providers after real class introspection as providers have less priority

    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    for (PyClassRef superClass : myClass.iterateAncestors()) {
      final PyClass pyClass = superClass.getPyClass();
      if (pyClass != null) {
        PsiElement superMember = resolveByMembersProviders(new PyClassTypeImpl(pyClass, isDefinition()), name);

        if (superMember != null) {
          return ResolveResultList.to(superMember);
        }
      }
    }


    return Collections.emptyList();
  }

  @Nullable
  private PyClassType getMetaclassType() {
    final PyTargetExpression metaClassAttribute = myClass.findClassAttribute(PyNames.DUNDER_METACLASS, true);
    if (metaClassAttribute != null) {
      final PyExpression metaclass = metaClassAttribute.findAssignedValue();
      if (metaclass instanceof PyReferenceExpression) {
        final QualifiedResolveResult result = ((PyReferenceExpression)metaclass).followAssignmentsChain(PyResolveContext.noImplicits());
        PsiElement element = result.getElement();
        if (element instanceof PyClass) {
          return new PyClassTypeImpl((PyClass)element, false);
        }
      }
    }
    return PyBuiltinCache.getInstance(myClass).getObjectType("type");
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    if (isDefinition()) {
      return new PyClassTypeImpl(getPyClass(), false);
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveClassMember(@NotNull PyClass cls,
                                               boolean isDefinition,
                                               @NotNull String name,
                                               @Nullable PyExpression location) {
    PsiElement result = resolveInner(cls, isDefinition, name, location);
    if (result != null) {
      return result;
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveByMembersProviders(PyClassType aClass, String name) {
    for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      final PsiElement resolveResult = provider.resolveMember(aClass, name);
      if (resolveResult != null) return resolveResult;
    }

    return null;
  }

  @Nullable
  private static PsiElement resolveInner(@NotNull PyClass cls, boolean isDefinition, @NotNull String name, @Nullable PyExpression location) {
    final ResolveProcessor processor = new ResolveProcessor(name);
    if (!isDefinition) {
      if (!cls.processInstanceLevelDeclarations(processor, location)) {
        return processor.getResult();
      }
    }
    cls.processClassLevelDeclarations(processor);
    return processor.getResult();
  }

  private static Key<Set<PyClassType>> CTX_VISITED = Key.create("PyClassType.Visited");
  public static Key<Boolean> CTX_SUPPRESS_PARENTHESES = Key.create("PyFunction.SuppressParentheses");

  public Object[] getCompletionVariants(String prefix, PyExpression location, ProcessingContext context) {
    Set<PyClassType> visited = context.get(CTX_VISITED);
    if (visited == null) {
      visited = new HashSet<PyClassType>();
      context.put(CTX_VISITED, visited);
    }
    if (visited.contains(this)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    visited.add(this);
    Set<String> namesAlready = context.get(CTX_NAMES);
    if (namesAlready == null) {
      namesAlready = new HashSet<String>();
    }
    List<Object> ret = new ArrayList<Object>();

    boolean suppressParentheses = context.get(CTX_SUPPRESS_PARENTHESES) != null;
    addOwnClassMembers(location, namesAlready, suppressParentheses, ret);

    addInheritedMembers(prefix, location, namesAlready, context, ret);

    // from providers
    for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      for (PyDynamicMember member : provider.getMembers(this)) {
        final String name = member.getName();
        if (!namesAlready.contains(name)) {
          LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(name).withIcon(member.getIcon()).withTypeText(getName());
          if (member.isFunction()) {
            lookupElementBuilder = lookupElementBuilder.withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS);
            lookupElementBuilder.withTailText("()");
          }
          ret.add(lookupElementBuilder);
        }
      }
    }

    if (!myClass.isNewStyleClass()) {
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(myClass);
      final PyClassType classobjType = cache.getOldstyleClassobjType();
      if (classobjType != null) {
        ret.addAll(Arrays.asList(classobjType.getCompletionVariants(prefix, location, context)));
      }
    }

    if (isDefinition() && myClass.isNewStyleClass()) {
      PyClassType typeType = getMetaclassType();
      if (typeType != null) {
        Collections.addAll(ret, typeType.getCompletionVariants(prefix, location, context));
      }
    }

    return ret.toArray();
  }

  private void addOwnClassMembers(PyExpression expressionHook, Set<String> namesAlready, boolean suppressParentheses, List<Object> ret) {
    PyClass containingClass = PsiTreeUtil.getParentOfType(expressionHook, PyClass.class);
    if (containingClass != null) {
      containingClass = CompletionUtil.getOriginalElement(containingClass);
    }
    boolean withinOurClass = containingClass == getPyClass() || isInSuperCall(expressionHook);

    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(
      expressionHook, new PyResolveUtil.FilterNotInstance(myClass), null
    );
    if (suppressParentheses) {
      processor.suppressParentheses();
    }
    myClass.processClassLevelDeclarations(processor);

    List<String> slots = myClass.isNewStyleClass() ? myClass.getSlots() : null;
    if (slots != null) {
      processor.setAllowedNames(slots);
    }
    myClass.processInstanceLevelDeclarations(processor, expressionHook);

    for (LookupElement le : processor.getResultList()) {
      String name = le.getLookupString();
      if (namesAlready.contains(name)) continue;
      if (!withinOurClass && isClassPrivate(name)) continue;
      namesAlready.add(name);
      ret.add(le);
    }
    if (slots != null) {
      for (String name : slots) {
        if (!namesAlready.contains(name)) {
          ret.add(LookupElementBuilder.create(name));
        }
      }
    }
  }

  private static boolean isInSuperCall(PyExpression hook) {
    if (hook instanceof PyReferenceExpression) {
      final PyExpression qualifier = ((PyReferenceExpression)hook).getQualifier();
      return qualifier instanceof PyCallExpression && ((PyCallExpression)qualifier).isCalleeText(PyNames.SUPER);
    }
    return false;
  }

  private void addInheritedMembers(String name,
                                   PyExpression expressionHook,
                                   Set<String> namesAlready,
                                   ProcessingContext context,
                                   List<Object> ret) {
    for (PyClass ancestor : myClass.getSuperClasses()) {
      Object[] ancestry = (new PyClassTypeImpl(ancestor, myIsDefinition)).getCompletionVariants(name, expressionHook, context);
      for (Object ob : ancestry) {
        String inheritedName = ob.toString();
        if (!namesAlready.contains(inheritedName) && !isClassPrivate(inheritedName)) {
          ret.add(ob);
          namesAlready.add(inheritedName);
        }
      }
      ContainerUtil.addAll(ret, ancestry);
    }
  }

  private static boolean isClassPrivate(String lookup_string) {
    return lookup_string.startsWith("__") && !lookup_string.endsWith("__");
  }

  public String getName() {
    return getPyClass().getName();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return PyBuiltinCache.getInstance(myClass).hasInBuiltins(myClass);
  }

  @Override
  public void assertValid(String message) {
    if (!myClass.isValid()) {
      throw new PsiInvalidElementAccessException(myClass, myClass.getClass().toString() + ": " + message);
    }
  }

  @NotNull
  public Set<String> getPossibleInstanceMembers() {
    Set<String> ret = new HashSet<String>();
    /*
    if (myClass != null) {
      PyClassType otype = PyBuiltinCache.getInstance(myClass.getProject()).getObjectType();
      ret.addAll(otype.getPossibleInstanceMembers());
    }
    */
    // TODO: add our own ideas here, e.g. from methods other than constructor
    return Collections.unmodifiableSet(ret);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyClassTypeImpl classType = (PyClassTypeImpl)o;

    if (myIsDefinition != classType.myIsDefinition) return false;
    if (!myClass.equals(classType.myClass)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myClass.hashCode();
    result = 31 * result + (myIsDefinition ? 1 : 0);
    return result;
  }

  public static boolean is(@NotNull String qName, PyType type) {
    if (type instanceof PyClassType) {
      return qName.equals(((PyClassType)type).getClassQName());
    }
    return false;
  }

  @Override
  public String toString() {
    return (isValid() ? "" : "[INVALID] ") + "PyClassType: " + getClassQName();
  }

  public boolean isValid() {
    return myClass.isValid();
  }

  @Nullable
  public static PyClassTypeImpl createTypeByQName(@NotNull Project project, String classQualifiedName, boolean isDefinition) {
    PyClass pyClass = PyClassNameIndex.findClass(classQualifiedName, project);
    if (pyClass == null) {
      return null;
    }
    return new PyClassTypeImpl(pyClass, isDefinition);
  }
}
