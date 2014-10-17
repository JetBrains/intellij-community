package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PyCustomMemberUtils;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Custom (aka dynamic) type that has some members you pass to it. To be used for cases like "type()"
 * This class can also mimic any other class (optionally). When mimics, it has all methods from this class and its own.
 * @author Ilya.Kazakevich
 */
public class PyCustomMembersType implements PyClassLikeType {
  @NotNull
  private final Map<String, PyCustomMember> myMembers;
  @Nullable
  private final PyClassType myTypeToMimic;

  /**
   * @param typeToMimic this type may mimic some other class-based type. Pass it to have all members from this class + custom.
   *                    Check class manual for more info.
   * @param members custom members
   */
  public PyCustomMembersType(@Nullable final PyClassType typeToMimic, @NotNull final PyCustomMember... members) {
    myTypeToMimic = typeToMimic;

    myMembers = new HashMap<String, PyCustomMember>(members.length);
    for (final PyCustomMember member : members) {
      myMembers.put(member.getName(), member);
    }
  }

  /**
   * @return class we mimic (if any). Check class manual for more info.
   */
  @Nullable
  public PyClassType getTypeToMimic() {
    return myTypeToMimic;
  }

  @Override
  public boolean isDefinition() {
    return false;
  }

  @Override
  public PyClassLikeType toInstance() {
    return this;
  }

  @Nullable
  @Override
  public String getClassQName() {
    return null;
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          @Nullable final PyExpression location,
                                                          @NotNull final AccessDirection direction,
                                                          @NotNull final PyResolveContext resolveContext,
                                                          final boolean inherited) {
    if (myMembers.containsKey(name)) {
      PsiElement context = null;
      if (location != null) {
        context = location;
      }
      if (context == null) {
        context = resolveContext.getTypeEvalContext().getOrigin();
      }
      if (context != null) {
        final PsiElement resolveResult = myMembers.get(name).resolve(context);
        if (resolveResult != null) {
          return Collections.singletonList(new RatedResolveResult(0, resolveResult));
        }
      }
    }
    if (myTypeToMimic != null) {
      return myTypeToMimic.resolveMember(name, location, direction, resolveContext, inherited);
    }
    return null;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Nullable
  @Override
  public PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited) {
    return null;
  }

  @Override
  public boolean isCallable() {
    return true;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return this;
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          @Nullable final PyExpression location,
                                                          @NotNull final AccessDirection direction,
                                                          @NotNull final PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @Override
  public Object[] getCompletionVariants(final String completionPrefix, final PsiElement location, final ProcessingContext context) {
    final Collection<LookupElement> lookupElements = new ArrayList<LookupElement>(myMembers.size());
    for (final PyCustomMember member : myMembers.values()) {
      lookupElements.add(PyCustomMemberUtils.toLookUpElement(member, member.getShortType()));
    }
    return ArrayUtil.mergeArrays(ArrayUtil.toObjectArray(lookupElements),
                                 ((myTypeToMimic != null)
                                  ? myTypeToMimic.getCompletionVariants(completionPrefix, location, context)
                                  : PsiElement.EMPTY_ARRAY));
  }


  @Nullable
  @Override
  public String getName() {
    String mimicName = null;
    if (myTypeToMimic != null) {
      mimicName = myTypeToMimic.getName();
    }
    if (mimicName != null) {
      return PyBundle.message("custom.type.mimic.name", mimicName);
    }
    return PyBundle.message("custom.type.name");
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(final String message) {

  }

  /**
   * @param name name to check
   * @return True if this class (not the one it mimics!) has member with passed name
   */
  public boolean hasMember(@NotNull final String name) {
    return myMembers.containsKey(name);
  }
}
