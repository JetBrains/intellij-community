package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyResolveUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyClassType implements PyType {

  protected PyClass myClass;
  protected boolean myIsDefinition;

  /**
   * Describes a class-based type. Since everyting in Python is an instance of some class, this type pretty much completes
   * the type system :)
   * Note that classes' and instances' member list can change during execution, so it is important to construct an instance of PyClassType
   * right in the place of reference, so that such changes could possibly be accounted for.
   * @param source PyClass which defines this type. For builtin or external classes, skeleton files contain the definitions.
   * @param is_definition whether this type describes an instance or a definition of the class.
   */
  public PyClassType(final @Nullable PyClass source, boolean is_definition) {
    myClass = source;
    myIsDefinition = is_definition;
  }

  /**
   * @return a PyClass which defined this type.
   */
  @Nullable
  public PyClass getPyClass() {
    return myClass;
  }

  /**
   * @return whether this type refers to an instance or a definition of the class.
   */
  public boolean isDefinition() {
    return myIsDefinition;
  }

  @Nullable
  public PsiElement resolveMember(final String name) {
    if (myClass == null) return null;
    PyResolveUtil.ResolveProcessor processor = new PyResolveUtil.ResolveProcessor(name);
    myClass.processDeclarations(processor, ResolveState.initial(), null, myClass); // our members are strictly within us.
    final PsiElement resolveResult = processor.getResult();
    //final PsiElement resolveResult = PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myClass, null, null);
    if (resolveResult != null) {
      return resolveResult;
    }
    PyExpression[] superClassExpressions = myClass.getSuperClassExpressions();
    if (superClassExpressions.length > 0) {
      for(PyExpression expr: superClassExpressions) {
        PyType superType = expr.getType();
        if (superType != null) {
          PsiElement superMember = superType.resolveMember(name);
          if (superMember != null) {
            return superMember;
          }
        }
      }
    }
    else {
      // no superclasses, try old-style
      // TODO: in py3k, 'object' is the default base, not <classobj>
      if (getClass() != null) {
        PyClassType oldstyle = PyBuiltinCache.getInstance(myClass.getProject()).getOldstyleClassobjType();
        if (oldstyle != null) {
          final PyClass myclass = getPyClass();
          if (myclass != null) {
            final String myname = myclass.getName();
            final PyClass oldstyleclass = oldstyle.getPyClass();
            if (oldstyleclass != null) {
              final String oldstylename = oldstyleclass.getName();
              if ((myname != null) && (oldstylename != null) && ! myname.equals(oldstylename) && !myname.equals("object")) {
                return oldstyle.resolveMember(name);
              }
            }
          }
        }
      }
    }
    return null;
  }

  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression) {
    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor(new PyResolveUtil.FilterNotInstance(myClass));
    myClass.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
    List<Object> ret = new ArrayList<Object>();
    ret.addAll(processor.getResultList());
    for (PyClass ancestor : myClass.getSuperClasses()) {
      ret.addAll(Arrays.asList((new PyClassType(ancestor, true)).getCompletionVariants(referenceExpression)));
    }
    return ret.toArray();
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
  
}
