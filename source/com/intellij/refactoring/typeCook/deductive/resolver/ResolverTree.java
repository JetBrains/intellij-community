package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiExtendedTypeVisitor;
import com.intellij.refactoring.typeCook.deductive.builder.Constraint;
import com.intellij.refactoring.typeCook.deductive.builder.Subtype;
import com.intellij.refactoring.typeCook.Bottom;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.ArrayUtil;

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

  private ResolverTree[] mySons = new ResolverTree[0];
  private BindingFactory myBindingFactory;
  private Binding myCurrentBinding;
  private SolutionHolder mySolutions;
  private Project myProject;

  private HashSet<Constraint> myConstraints;

  public ResolverTree(final com.intellij.refactoring.typeCook.deductive.builder.System system) {
    myBindingFactory = new BindingFactory(system);
    mySolutions = new SolutionHolder();
    myCurrentBinding = myBindingFactory.create();
    myConstraints = system.getConstraints();
    myProject = system.getProject();

    reduceCyclicVariables();
  }

  private ResolverTree(final ResolverTree parent, final HashSet<Constraint> constraints, final Binding binding) {
    myBindingFactory = parent.myBindingFactory;
    myCurrentBinding = binding;
    mySolutions = parent.mySolutions;
    myConstraints = constraints;
    myProject = parent.myProject;
  }

  private HashSet<Constraint> apply(final Binding b, final HashSet<Constraint> constraints) {
    final HashSet<Constraint> result = new HashSet<Constraint>();

    for (Iterator<Constraint> c = constraints.iterator(); c.hasNext();) {
      final Constraint constr = c.next();
      result.add(constr.apply(b));
    }

    return result;
  }

  private ResolverTree applyRule(final Binding b) {
    final Binding newBinding = myCurrentBinding.compose(b);

    return newBinding == null ? null : new ResolverTree(this, apply(b, myConstraints), newBinding);
  }

  private void reduceCyclicVariables() {
    final HashSet<PsiTypeVariable> nodes = new HashSet<PsiTypeVariable>();
    final HashSet<Constraint> candidates = new HashSet<Constraint>();

    final HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>> ins = new HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>>();
    final HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>> outs = new HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>>();

    for (Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
      final Constraint constraint = c.next();

      final PsiType left = constraint.getLeft();
      final PsiType right = constraint.getRight();

      if (left instanceof PsiTypeVariable && right instanceof PsiTypeVariable) {
        final PsiTypeVariable leftVar = (PsiTypeVariable)left;
        final PsiTypeVariable rightVar = (PsiTypeVariable)right;

        candidates.add(constraint);

        nodes.add(leftVar);
        nodes.add(rightVar);

        final HashSet<PsiTypeVariable> in = ins.get(leftVar);
        final HashSet<PsiTypeVariable> out = outs.get(rightVar);

        if (in == null) {
          final HashSet<PsiTypeVariable> newIn = new HashSet<PsiTypeVariable>();

          newIn.add(rightVar);

          ins.put(leftVar, newIn);
        }
        else {
          in.add(rightVar);
        }

        if (out == null) {
          final HashSet<PsiTypeVariable> newOut = new HashSet<PsiTypeVariable>();

          newOut.add(leftVar);

          outs.put(rightVar, newOut);
        }
        else {
          out.add(leftVar);
        }
      }
    }

    final DFSTBuilder<PsiTypeVariable> dfstBuilder = new DFSTBuilder<PsiTypeVariable>(new Graph<PsiTypeVariable>() {
                                                                                        public Collection<PsiTypeVariable> getNodes() {
                                                                                          return nodes;
                                                                                        }

                                                                                        public Iterator<PsiTypeVariable> getIn(final PsiTypeVariable n) {
                                                                                          final HashSet<PsiTypeVariable> in = ins.get(n);

                                                                                          if (in == null) {
                                                                                            return new HashSet<PsiTypeVariable>().iterator();
                                                                                          }

                                                                                          return in.iterator();
                                                                                        }

                                                                                        public Iterator<PsiTypeVariable> getOut(final PsiTypeVariable n) {
                                                                                          final HashSet<PsiTypeVariable> out = outs.get(n);

                                                                                          if (out == null) {
                                                                                            return new HashSet<PsiTypeVariable>().iterator();
                                                                                          }

                                                                                          return out.iterator();
                                                                                        }
                                                                                      });

    final LinkedList<Pair<Integer, Integer>> sccs = dfstBuilder.getSCCs();
    final HashMap<PsiTypeVariable, Integer> index = new HashMap<PsiTypeVariable, Integer>();

    for (Iterator<Pair<Integer, Integer>> i = sccs.iterator(); i.hasNext();) {
      final Pair<Integer, Integer> p = i.next();
      final Integer biT = p.getFirst();
      final int binum = biT.intValue();

      for (int j = 0; j < p.getSecond().intValue(); j++) {
        index.put(dfstBuilder.getNodeByTNumber(binum + j), biT);
      }
    }

    for (Iterator<Constraint> c = candidates.iterator(); c.hasNext();) {
      final Constraint constraint = c.next();

      if (index.get(constraint.getLeft()).equals(index.get(constraint.getRight()))) {
        myConstraints.remove(constraint);
      }
    }

    Binding binding = myBindingFactory.create();

    for (Iterator<PsiTypeVariable> v = index.keySet().iterator(); v.hasNext();) {
      final PsiTypeVariable fromVar = v.next();
      final PsiTypeVariable toVar = dfstBuilder.getNodeByNNumber(index.get(fromVar).intValue());

      if (!fromVar.equals(toVar)) {
        binding = binding.compose(myBindingFactory.create(fromVar, toVar));

        if (binding == null) {
        break;
        }
      }
    }

    if (binding != null && binding.nonEmpty()) {
      myCurrentBinding = myCurrentBinding.compose(binding);
      myConstraints = apply(binding, myConstraints);
    }
  }

  private static class PsiTypeVarCollector extends PsiExtendedTypeVisitor {
    final HashSet<PsiTypeVariable> mySet = new HashSet<PsiTypeVariable>();

    public Object visitTypeVariable(final PsiTypeVariable var) {
      mySet.add (var);

      return null;
    }

    public HashSet<PsiTypeVariable> getSet (final PsiType type){
      type.accept(this);
      return mySet;
    }
  }

  private void reduceTypeType(final Constraint constr) {
    final PsiType left = constr.getLeft();
    final PsiType right = constr.getRight();

    Binding riseBinding = myBindingFactory.rise(left, right);
    Binding sinkBinding = myBindingFactory.sink(left, right);

    int indicator = (riseBinding == null ? 0 : 1) + (sinkBinding == null ? 0 : 1);

    if (indicator == 0) {
      return;
    }
    else if (indicator == 2) {
      switch (riseBinding.compare(sinkBinding)) {
      case Binding.SAME:
      //case Binding.BETTER:
           indicator = 1;
           sinkBinding = null;
      break;

      //case Binding.WORSE:
      //     indicator = 1;
      //     riseBinding = null;
      //break;
      }
    }

    myConstraints.remove(constr);

    mySons = new ResolverTree[indicator];

    int n = 0;

    if (riseBinding != null) {
      mySons[n++] = applyRule(riseBinding);
    }

    if (sinkBinding != null) {
      mySons[n++] = applyRule(sinkBinding);
    }
  }

  private interface Mapper {
    PsiType map(PsiType t);
  }

  private void fillTypeRange(final PsiType lowerBound,
                             final PsiType upperBound,
                             final HashSet<PsiType> holder,
                             final Mapper mapper) {
    if (lowerBound instanceof PsiClassType && upperBound instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resultLower = ((PsiClassType)lowerBound).resolveGenerics();
      final PsiClassType.ClassResolveResult resultUpper = ((PsiClassType)upperBound).resolveGenerics();

      final PsiClass lowerClass = resultLower.getElement();
      final PsiClass upperClass = resultUpper.getElement();

      if (lowerClass != null && upperClass != null && !lowerClass.equals(upperClass)) {
        final PsiSubstitutor upperSubst = resultUpper.getSubstitutor();
        final PsiClass[] parents = upperClass.getSupers();
        final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();

        for (int i = 0; i < parents.length; i++) {
          final PsiClass parent = parents[i];

          if (InheritanceUtil.isCorrectDescendant(parent, lowerClass, true)) {
            final PsiClassType type = factory.createType(parent,
                                                         TypeConversionUtil.getSuperClassSubstitutor(parent, upperClass, upperSubst));
            holder.add(mapper.map(type));
            fillTypeRange(lowerBound, type, holder, mapper);
          }
        }
      }
    }
    else if (lowerBound instanceof PsiArrayType && upperBound instanceof PsiArrayType) {
      fillTypeRange(((PsiArrayType)lowerBound).getComponentType(), ((PsiArrayType)upperBound).getComponentType(), holder, new Mapper() {
                                                public PsiType map(PsiType t) {
                                                  return mapper.map(t.createArrayType());
                                                }
                                              });
    }
  }

  private PsiType[] getTypeRange(final PsiType lowerBound, final PsiType upperBound) {
    final HashSet<PsiType> range = new HashSet<PsiType>();

    range.add(lowerBound);
    range.add(upperBound);

    fillTypeRange(lowerBound, upperBound, range, new Mapper() {
                    public PsiType map(PsiType t) {
                      return t;
                    }
                  });

    return range.toArray(new PsiType[]{});
  }

  private void reduceInterval(final Constraint left, final Constraint right) {
    final PsiType leftType = left.getLeft();
    final PsiType rightType = right.getRight();
    final PsiTypeVariable var = (PsiTypeVariable)left.getRight();

    if (leftType.equals(rightType)) {
      final Binding binding = myBindingFactory.create(var, leftType);

      myConstraints.remove(left);
      myConstraints.remove(right);

      mySons = new ResolverTree[]{applyRule(binding)};

      return;
    }

    Binding riseBinding = myBindingFactory.rise(leftType, rightType);
    Binding sinkBinding = myBindingFactory.sink(leftType, rightType);

    int indicator = (riseBinding == null ? 0 : 1) + (sinkBinding == null ? 0 : 1);

    if (indicator == 0) {
      return;
    }
    else if (indicator == 2) {
      switch (riseBinding.compare(sinkBinding)) {
      case Binding.SAME:
           indicator = 1;
           sinkBinding = null;
      break;

           //case Binding.WORSE:
           //  indicator = 1;
           //  riseBinding = null;
           //  break;
      }
    }

    PsiType[] riseRange = new PsiType[]{};
    PsiType[] sinkRange = new PsiType[]{};

    if (riseBinding != null) {
      riseRange = getTypeRange(riseBinding.apply(rightType), riseBinding.apply(leftType));
    }

    if (sinkBinding != null) {
      sinkRange = getTypeRange(sinkBinding.apply(rightType), sinkBinding.apply(leftType));
    }

    if (riseRange.length + sinkRange.length > 0) {
      myConstraints.remove(left);
      myConstraints.remove(right);
    }

    mySons = new ResolverTree[riseRange.length + sinkRange.length];

    for (int i = 0; i < riseRange.length; i++) {
      final PsiType type = riseRange[i];

      mySons[i] = applyRule(riseBinding.compose(myBindingFactory.create(var, type)));
    }

    for (int i = 0; i < sinkRange.length; i++) {
      final PsiType type = sinkRange[i];

      mySons[i + riseRange.length] = applyRule(sinkBinding.compose(myBindingFactory.create(var, type)));
    }
  }

  private void reduce() {
    if (myConstraints.size() == 0) {
      return;
    }

    if (myCurrentBinding.isCyclic()) {
      reduceCyclicVariables();
    }

    final HashMap<PsiTypeVariable, Constraint> myTypeVarConstraints = new HashMap<PsiTypeVariable, Constraint>();
    final HashMap<PsiTypeVariable, Constraint> myVarTypeConstraints = new HashMap<PsiTypeVariable, Constraint>();

    for (Iterator<Constraint> i = myConstraints.iterator(); i.hasNext();) {
      final Constraint constr = i.next();

      final PsiType left = constr.getLeft();
      final PsiType right = constr.getRight();

      switch ((left instanceof PsiTypeVariable ? 0 : 1) + (right instanceof PsiTypeVariable ? 0 : 2)) {
      case 0:
      continue;

      case 1:
           {
             final Constraint c = myTypeVarConstraints.get(right);

             if (c == null) {
               final Constraint d = myVarTypeConstraints.get(right);

               if (d != null) {
                 reduceInterval(constr, d);
                 return;
               }

               myTypeVarConstraints.put((PsiTypeVariable)right, constr);
             }
             else {
               reduceTypeVar(constr, c);
               return;
             }
           }
      break;

      case 2:
           {
             final Constraint c = myVarTypeConstraints.get(left);

             if (c == null) {
               final Constraint d = myTypeVarConstraints.get(left);

               if (d != null) {
                 reduceInterval(d, constr);
                 return;
               }

               myVarTypeConstraints.put((PsiTypeVariable)left, constr);
             }
             else {
               reduceVarType(constr, c);
               return;
             }
           break;
           }

      case 3:
           reduceTypeType(constr);
           return;
      }
    }

    for (final Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
      final Constraint constr = c.next();
      final PsiType left = constr.getLeft();
      final PsiType right = constr.getRight();

      if (!(left instanceof PsiTypeVariable) && right instanceof PsiTypeVariable) {
        final PsiType leftType = left instanceof PsiWildcardType ? ((PsiWildcardType)left).getBound() : left;
        final PsiManager manager = PsiManager.getInstance(myProject);
        final PsiType[] types = getTypeRange(PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(myProject)), leftType);

        mySons = new ResolverTree[types.length];

        if (types.length > 0) {
          myConstraints.remove(constr);
        }

        for (int i = 0; i < types.length; i++) {
          final PsiType type = types[i];
          mySons[i] = applyRule(myBindingFactory.create(((PsiTypeVariable)right), type));
        }

        return;
      }
    }

    final HashSet<PsiTypeVariable> haveLeftBound = new HashSet<PsiTypeVariable>();

    Constraint target = null;
    final HashSet<PsiTypeVariable> boundVariables = new HashSet<PsiTypeVariable>();

    for (final Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
      final Constraint constr = c.next();
      final PsiType leftType = constr.getLeft();
      final PsiType rightType = constr.getRight();

      if (leftType instanceof PsiTypeVariable) {
        boundVariables.add((PsiTypeVariable)leftType);

        if (rightType instanceof PsiTypeVariable) {
          boundVariables.add((PsiTypeVariable)rightType);
          haveLeftBound.add(((PsiTypeVariable)rightType));
        }
        else if (!Util.bindsTypeVariables(rightType)) {
          target = constr;
        }
      }
      else {
        LOG.error("Must not happen");
      }
    }

    if (target == null) {
      Binding binding = myBindingFactory.create();

      for (final Iterator<PsiTypeVariable> v = myBindingFactory.getBoundVariables().iterator(); v.hasNext();) {
        final PsiTypeVariable var = v.next();

        if (!myCurrentBinding.binds(var) && !boundVariables.contains(var)) {
          binding = binding.compose(myBindingFactory.create(var, Bottom.BOTTOM));
        }
      }

      if (!binding.nonEmpty()) {
        myConstraints.clear();
      }

      mySons = new ResolverTree[]{applyRule(binding)};
    }
    else {
      final PsiType type = target.getRight();
      final PsiTypeVariable var = (PsiTypeVariable)target.getLeft();

      final Binding binding =
        haveLeftBound.contains(var)
        ? myBindingFactory.create(var, type)
        : myBindingFactory.create(var, PsiWildcardType.createExtends(PsiManager.getInstance(myProject), type));

      myConstraints.remove(target);

      mySons = new ResolverTree[]{applyRule(binding)};
    }
  }

  private void logSolution() {
    LOG.debug("Reduced system:");

    for (final Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
      final Constraint constr = c.next();

      LOG.debug(constr.toString());
    }

    LOG.debug("End of Reduced system.");
    LOG.debug("Reduced binding:");
    LOG.debug(myCurrentBinding.toString());
    LOG.debug("End of Reduced binding.");
  }

  private interface Reducer {
    LinkedList<Pair<PsiType, Binding>> unify(PsiType x, PsiType y);

    Constraint create(PsiTypeVariable var, PsiType type);

    PsiType getType(Constraint c);

    PsiTypeVariable getVar(Constraint c);
  }

  private void reduceTypeVar(final Constraint x, final Constraint y) {
    reduceSideVar(x, y, new Reducer() {
                    public LinkedList<Pair<PsiType, Binding>> unify(final PsiType x, final PsiType y) {
                      return myBindingFactory.intersect(x, y);
                    }

                    public Constraint create(final PsiTypeVariable var, final PsiType type) {
                      return new Subtype(type, var);
                    }

                    public PsiType getType(final Constraint c) {
                      return c.getLeft();
                    }

                    public PsiTypeVariable getVar(final Constraint c) {
                      return (PsiTypeVariable)c.getRight();
                    }
                  });
  }

  private void reduceVarType(final Constraint x, final Constraint y) {
    reduceSideVar(x, y, new Reducer() {
                    public LinkedList<Pair<PsiType, Binding>> unify(final PsiType x, final PsiType y) {
                      return myBindingFactory.union(x, y);
                    }

                    public Constraint create(final PsiTypeVariable var, final PsiType type) {
                      return new Subtype(var, type);
                    }

                    public PsiType getType(final Constraint c) {
                      return c.getRight();
                    }

                    public PsiTypeVariable getVar(final Constraint c) {
                      return (PsiTypeVariable)c.getLeft();
                    }
                  });
  }

  private void reduceSideVar(final Constraint x, final Constraint y, final Reducer reducer) {
    final PsiTypeVariable var = reducer.getVar(x);

    final PsiType xType = reducer.getType(x);
    final PsiType yType = reducer.getType(y);

    final LinkedList<Pair<PsiType, Binding>> union = reducer.unify(xType, yType);

    if (union.size() == 0) {
      return;
    }

    myConstraints.remove(x);
    myConstraints.remove(y);

    mySons = new ResolverTree[union.size()];
    int i = 0;

    Constraint prev = null;

    for (Iterator<Pair<PsiType, Binding>> p = union.iterator(); p.hasNext();) {
      final Pair<PsiType, Binding> pair = p.next();

      if (prev != null) {
        myConstraints.remove(prev);
      }

      prev = reducer.create(var, pair.getFirst());
      myConstraints.add(prev);

      mySons[i++] = applyRule(pair.getSecond());
    }
  }

  public void resolve() {
    reduce();

    if (mySons.length > 0) {
      for (int i = 0; i < mySons.length; i++) {
        final ResolverTree son = mySons[i];

        if (son != null) {
          son.resolve();
        }
      }
    }
    else {
      if (myConstraints.size() == 0) {
        logSolution();

        mySolutions.putSolution(myCurrentBinding);
      }
    }
  }

  public Binding[] getSolutions() {
    return mySolutions.getBestSolutions();
  }
}
