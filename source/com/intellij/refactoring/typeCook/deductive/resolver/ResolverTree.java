package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiExtendedTypeVisitor;
import com.intellij.refactoring.typeCook.deductive.builder.Constraint;

import javax.swing.text.html.HTMLDocument;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Dec 27, 2004
 * Time: 4:57:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class ResolverTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree");

  private HashSet<Constraint> myConstraints;
  private ResolverTree myParent;
  private ResolverTree[] mySons = new ResolverTree[0];
  private BindingFactory myBindingFactory;
  private Binding myCurrentBinding;

  public ResolverTree(final com.intellij.refactoring.typeCook.deductive.builder.System system) {
    myBindingFactory = new BindingFactory(system.getVariableFactory());
    myConstraints = system.getConstraints();
    myCurrentBinding = myBindingFactory.create();
    myParent = null;
  }

  private ResolverTree(final ResolverTree parent, final HashSet<Constraint> constaints, final Binding binding) {
    myBindingFactory = parent.myBindingFactory;
    myConstraints = constaints;
    myCurrentBinding = binding;
    myParent = parent;
  }

  private HashSet<Constraint> apply(final Binding b, final Constraint omit) {
    final HashSet<Constraint> result = new HashSet<Constraint>();

    for (Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
      final Constraint constr = c.next();

      if (constr == omit) {
        continue;
      }

      result.add(constr.apply(b));
    }

    return result;
  }

  private ResolverTree applyRule(final Binding b, final Constraint omit) {
    return new ResolverTree(this, apply(b, omit), myCurrentBinding.compose(b));
  }

  private boolean reduceTypeType() {
    for (Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
      final Constraint constr = c.next();

      final PsiType left = constr.getLeft();
      final PsiType right = constr.getRight();

      if (!(left instanceof PsiTypeVariable) && !(right instanceof PsiTypeVariable)) {
        final Binding riseBinding = myBindingFactory.rise(left, right);
        final Binding sinkBinding = myBindingFactory.sink(left, right);

        final int indicator = (riseBinding == null ? 0 : 1) + (sinkBinding == null ? 0 : 1);

        if (indicator == 0) {
          return false;
        }

        mySons = new ResolverTree[indicator];

        int n = 0;

        if (riseBinding != null) {
          mySons[n++] = applyRule(riseBinding, constr);
        }

        if (sinkBinding != null) {
          mySons[n++] = applyRule(sinkBinding, constr);
        }

        return true;
      }
    }

    return false;
  }

  private boolean solved (){
    return myConstraints.size() == 0;
  }

  private void resolve (final List<Binding> solutions){
    if (solved()){
      solutions.add(myCurrentBinding);
    }

    if (reduceTypeType ()){
      for (int i = 0; i < mySons.length; i++) {
        mySons[i].resolve(solutions);
      }
    }
  }

  public List<Binding> resolve (){
    final LinkedList<Binding> list = new LinkedList<Binding>();

    resolve(list);

    return list;
  }
}
