package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiExtendedTypeVisitor;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.Bottom;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jan 13, 2005
 * Time: 3:46:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class BindingFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.resolver.BindingFactory");

  private int myVariablesNumber;
  private int[] myBoundVariableIndices;
  private HashSet<PsiTypeVariable> myBoundVariables;
  private Project myProject;
  private PsiTypeVariableFactory myFactory;

  private PsiClass[] getGreatestLowerClasses(final PsiClass aClass, final PsiClass bClass) {
    if (InheritanceUtil.isInheritorOrSelf(aClass, bClass, true)) {
      return new PsiClass[]{aClass};
    }

    if (InheritanceUtil.isInheritorOrSelf(bClass, aClass, true)) {
      return new PsiClass[]{bClass};
    }

    final Set<PsiClass> descendants = new LinkedHashSet<PsiClass>();

    new Object() {
      public void getGreatestLowerClasses(final PsiClass aClass, final PsiClass bClass, final Set<PsiClass> descendants) {
        if (aClass.isInheritor(bClass, true)) {
          descendants.add(bClass);
        }
        else {
          final PsiSearchHelper helper = aClass.getManager().getSearchHelper();
          final PsiClass[] bSubs = helper.findInheritors(bClass, GlobalSearchScope.allScope(myProject), false);
          for (int i = 0; i < bSubs.length; i++) {
            getGreatestLowerClasses(bSubs[i], aClass, descendants);
          }
        }
      }
    }.getGreatestLowerClasses(aClass, bClass, descendants);

    return descendants.toArray(new PsiClass[descendants.size()]);
  }

  private class BindingImpl extends Binding {
    private PsiType[] myBindings;
    private boolean myCyclic;

    BindingImpl(final PsiTypeVariable var, final PsiType type) {
      myBindings = new PsiType[myVariablesNumber];
      myCyclic = type instanceof PsiTypeVariable;

      myBindings[var.getIndex()] = type;
    }

    BindingImpl(final int index, final PsiType type) {
      myBindings = new PsiType[myVariablesNumber];
      myCyclic = type instanceof PsiTypeVariable;

      myBindings[index] = type;

      if (type instanceof Bottom) {
        final HashSet<PsiTypeVariable> cluster = myFactory.getClusterOf(index);

        if (cluster != null) {
          for (final Iterator<PsiTypeVariable> v = cluster.iterator(); v.hasNext();) {
            myBindings[v.next().getIndex()] = type;
          }
        }
      }
    }

    BindingImpl(final int n) {
      myBindings = new PsiType[n];
      myCyclic = false;
    }

    public PsiType apply(final PsiType type) {
      if (type instanceof PsiTypeVariable) {
        final PsiType t = myBindings[((PsiTypeVariable)type).getIndex()];
        return t == null ? type : t;
      }
      else if (type instanceof PsiArrayType) {
        return apply(((PsiArrayType)type).getComponentType()).createArrayType();
      }
      else if (type instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult result = Util.resolveType(type);
        final PsiClass theClass = result.getElement();
        final PsiSubstitutor aSubst = result.getSubstitutor();

        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        if (theClass != null) {
          for (Iterator<PsiTypeParameter> p = aSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
            final PsiTypeParameter aParm = p.next();
            final PsiType aType = aSubst.substitute(aParm);

            theSubst = theSubst.put(aParm, apply(aType));
          }

          return theClass.getManager().getElementFactory().createType(theClass, theSubst);
        }
        else {
          return null;
        }
      }
      else if (type instanceof PsiWildcardType) {
        final PsiWildcardType wcType = (PsiWildcardType)type;
        final PsiType bound = wcType.getBound();

        if (bound != null) {
          return PsiWildcardType.createExtends(PsiManager.getInstance(myProject), apply(bound));
        }

        return type;
      }
      else {
        return type;
      }
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BindingImpl)) return false;

      final BindingImpl binding = (BindingImpl)o;

      if (!Arrays.equals(myBindings, binding.myBindings)) return false;

      return true;
    }

    public Binding compose(final Binding b) {
      LOG.assertTrue(b instanceof BindingImpl);

      final BindingImpl b1 = this;
      final BindingImpl b2 = (BindingImpl)b;

      LOG.assertTrue(b1.myBindings.length == b2.myBindings.length);

      final BindingImpl b3 = new BindingImpl(b1.myBindings.length);

      for (int i = 0; i < myBindings.length; i++) {
        final PsiType b1i = b1.myBindings[i];
        final PsiType b2i = b2.myBindings[i];

        final int flag = (b1i == null ? 0 : 1) + (b2i == null ? 0 : 2);

        switch (flag) {
        case 0:
        break;

        case 1: /* b1(i)\b2(i) */
             {
               final PsiType type = b2.apply(b1i);
               b3.myBindings[i] = type;
               b3.myCyclic = type instanceof PsiTypeVariable;
             }
        break;

        case 2: /* b2(i)\b1(i) */
             {
               final PsiType type = b1.apply(b2i);
               b3.myBindings[i] = type;
               b3.myCyclic = type instanceof PsiTypeVariable;
             }
        break;

        case 3:  /* b2(i) \cap b1(i) */
             final Binding common = rise(b1i, b2i);

             if (common == null) {
               return null;
             }

             final PsiType type = b2.apply(common.apply(b1i));
             b3.myBindings[i] = type;
             b3.myCyclic = type instanceof PsiTypeVariable;
        }
      }

      return b3;
    }

    public String toString() {
      final StringBuffer buffer = new StringBuffer();

      for (int i = 0; i < myBindings.length; i++) {
        final PsiType binding = myBindings[i];

        if (binding != null) {
          buffer.append("#" + i + " -> " + binding.getPresentableText() + "; ");
        }
      }

      return buffer.toString();
    }

    private PsiType normalize(final PsiType t) {
      if (t == null || t instanceof PsiTypeVariable) {
        return Bottom.BOTTOM;
      }

      if (t instanceof PsiWildcardType) {
        return ((PsiWildcardType)t).getBound();
      }

      return t;
    }

    public int compare(final Binding binding) {
      final BindingImpl b2 = (BindingImpl)binding;
      final BindingImpl b1 = this;

      int directoin = Binding.NONCOMPARABLE;
      boolean first = true;

      for (int i = 0; i < myBoundVariableIndices.length; i++) {
        final int index = myBoundVariableIndices[i];

        final PsiType x = normalize(b1.myBindings[index]);
        final PsiType y = normalize(b2.myBindings[index]);

        final int comp = new Object() {
          int compare(final PsiType x, final PsiType y) {
            final int[] kinds = new Object() {
              private int classify(final PsiType x) {
                if (x == null) {
                  return 0;
                }

                if (x instanceof PsiPrimitiveType) {
                  return 1;
                }

                if (x instanceof PsiArrayType) {
                  return 2;
                }

                if (x instanceof PsiClassType) {
                  return 3;
                }

                return 4; // Bottom
              }

              int[] classify2(final PsiType x, final PsiType y) {
                return new int[]{classify(x), classify(y)};
              }
            }.classify2(x, y);

            final int kindX = kinds[0];
            final int kindY = kinds[1];

            // Break your brain here...
            if (kindX + kindY == 0) {
              return Binding.SAME;
            }

            if (kindX * kindY == 0) {
              if (kindX == 0) {
                return Binding.WORSE;
              }

              return Binding.BETTER;
            }

            if (kindX * kindY == 1) {
              if (x.equals(y)) {
                return Binding.SAME;
              }

              return Binding.NONCOMPARABLE;
            }

            if (kindX != kindY) {
              if (kindX == 4) {
                return Binding.WORSE;
              }

              if (kindY == 4) {
                return Binding.BETTER;
              }

              if (kindX + kindY == 5) {
                try {
                  final PsiElementFactory f = PsiManager.getInstance(myProject).getElementFactory();
                  final PsiType cloneable = f.createTypeFromText("java.lang.Cloneable", null);
                  final PsiType object = f.createTypeFromText("java.lang.Object", null);
                  final PsiType serializable = f.createTypeFromText("java.io.Serializable", null);

                  PsiType type = null;
                  int flag = 0;

                  if (kindX == 3) {
                    type = x;
                    flag = Binding.WORSE;
                  }
                  else {
                    type = y;
                    flag = Binding.BETTER;
                  }

                  if (type.equals(object) || type.equals(cloneable) || type.equals(serializable)) {
                    return flag;
                  }
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }

              return Binding.NONCOMPARABLE;
            }

            if (kindX == 2) {
              return compare(((PsiArrayType)x).getComponentType(), ((PsiArrayType)y).getComponentType());
            }

            if (x.equals(y)) {
              return Binding.SAME;
            }
            // End of breaking...

            final PsiClassType.ClassResolveResult resultX = Util.resolveType(x);
            final PsiClassType.ClassResolveResult resultY = Util.resolveType(y);

            final PsiClass xClass = resultX.getElement();
            final PsiClass yClass = resultY.getElement();

            final PsiSubstitutor xSubst = resultX.getSubstitutor();
            final PsiSubstitutor ySubst = resultY.getSubstitutor();

            if (xClass == null || yClass == null) {
              return Binding.NONCOMPARABLE;
            }

            if (xClass.equals(yClass)) {
              boolean first = true;
              int direction = Binding.SAME;

              for (Iterator<PsiTypeParameter> i = xSubst.getSubstitutionMap().keySet().iterator(); i.hasNext();) {
                final PsiTypeParameter p = i.next();

                final PsiType xParm = xSubst.substitute(p);
                final PsiType yParm = ySubst.substitute(p);

                final int comp = compare(xParm, yParm);

                if (comp == Binding.NONCOMPARABLE) {
                  return Binding.NONCOMPARABLE;
                }

                if (first) {
                  first = false;
                  direction = comp;
                }

                if (direction != comp) {
                  return Binding.NONCOMPARABLE;
                }
              }

              return direction;
            }
            else {
              if (InheritanceUtil.isCorrectDescendant(xClass, yClass, true)) {
                return Binding.BETTER;
              }
              else if (InheritanceUtil.isCorrectDescendant(yClass, xClass, true)) {
                return Binding.WORSE;
              }

              return Binding.NONCOMPARABLE;
            }
          }
        }.compare(x, y);

        if (comp == Binding.NONCOMPARABLE) {
          return Binding.NONCOMPARABLE;
        }

        if (first) {
          first = false;
          directoin = comp;
        }

        if (directoin != SAME) {
          if (comp != Binding.SAME && directoin != comp) {
            return Binding.NONCOMPARABLE;
          }
        }
        else if (comp != SAME) {
          directoin = comp;
        }
      }

      return directoin;
    }

    public boolean nonEmpty() {
      for (int i = 0; i < myBindings.length; i++) {
        if (myBindings[i] != null) {
          return true;
        }
      }

      return false;
    }

    public boolean isCyclic() {
      return myCyclic;
    }

    public Binding reduceRecursive() {
      final BindingImpl binding = (BindingImpl)create();

      for (final Iterator<PsiTypeVariable> v = myBoundVariables.iterator(); v.hasNext();) {
        final PsiTypeVariable var = v.next();
        final int index = var.getIndex();
        final PsiType type = myBindings[index];

        if (type != null) {
          class Verifier extends PsiExtendedTypeVisitor {
            boolean myFlag = false;

            public Object visitTypeVariable(final PsiTypeVariable var) {
              if (var.getIndex() == index) {
                myFlag = true;
              }

              return null;
            }
          }

          final Verifier verifier = new Verifier();

          type.accept(verifier);

          if (verifier.myFlag) {
            myBindings[index] = Bottom.BOTTOM;
            binding.myBindings[index] = Bottom.BOTTOM;
          }
          else {
            binding.myBindings[index] = type;
          }
        }
        else {
          binding.myBindings[index] = type;
        }
      }

      for (final Iterator<PsiTypeVariable> v = myBoundVariables.iterator(); v.hasNext();) {
        final PsiTypeVariable var = v.next();
        final int index = var.getIndex();
        final PsiType type = myBindings[index];

        if (type != null) {
          myBindings[index] = binding.apply(type);
        }
      }

      return this;
    }

    public boolean binds(final PsiTypeVariable var) {
      return myBindings[var.getIndex()] != null;
    }

    public void merge(final Binding b, final boolean removeObject) {
      for (final Iterator<PsiTypeVariable> v = b.getBoundVariables().iterator(); v.hasNext();) {
        final PsiTypeVariable var = v.next();
        final int index = var.getIndex();

        if (myBindings[index] != null) {
          LOG.error("Oops... Binding conflict...");
        }
        else {
          final PsiType type = b.apply(var);
          final PsiClassType javaLangObject =
          PsiType.getJavaLangObject(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));

          if (removeObject &&
              javaLangObject.equals(type)) {
            final HashSet<PsiTypeVariable> cluster = myFactory.getClusterOf(var.getIndex());

            if (cluster != null) {
              for (final Iterator<PsiTypeVariable> w = cluster.iterator(); w.hasNext();) {
                final PsiTypeVariable war = (PsiTypeVariable)w.next();
                final PsiType wtype = b.apply(war);

                if (!javaLangObject.equals(wtype)) {
                  myBindings[index] = type;
                break;
                }
              }
            }
          }
          else {
            myBindings[index] = type;
          }
        }
      }
    }

    public HashSet<PsiTypeVariable> getBoundVariables() {
      return myBoundVariables;
    }

    public int getWidth() {
      int w = 0;

      for (int i = 0; i < myBindings.length; i++) {
        final PsiType type = substitute(myBindings[i]);

        if (type != null) {
          w++;
        }
      }

      return w;
    }

    public PsiType substitute(final PsiType t) {
      if (t instanceof PsiWildcardType) {
        final PsiWildcardType wcType = (PsiWildcardType)t;
        final PsiType bound = wcType.getBound();

        if (bound == null) {
          return t;
        }

        final PsiManager manager = PsiManager.getInstance(myProject);
        final PsiType subst = substitute(bound);
        return subst instanceof PsiWildcardType ? subst : wcType.isExtends()
                                                          ? PsiWildcardType.createExtends(manager, subst)
                                                          : PsiWildcardType.createSuper(manager, subst);
      }
      else if (t instanceof PsiTypeVariable) {
        final PsiType b = apply(t);

        if (b instanceof Bottom || b instanceof PsiTypeVariable) {
          return null;
        }

        return substitute(b);
      }
      else if (t instanceof Bottom) {
        return null;
      }
      else if (t instanceof PsiArrayType) {
        return substitute(((PsiArrayType)t).getComponentType()).createArrayType();
      }
      else if (t instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult result = ((PsiClassType)t).resolveGenerics();

        final PsiClass aClass = result.getElement();
        final PsiSubstitutor aSubst = result.getSubstitutor();

        if (aClass == null) {
          return t;
        }

        PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

        for (final Iterator<PsiTypeParameter> p = aSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
          final PsiTypeParameter parm = p.next();
          final PsiType type = aSubst.substitute(parm);

          theSubst = theSubst.put(parm, substitute(type));
        }

        return aClass.getManager().getElementFactory().createType(aClass, theSubst);
      }
      else {
        return t;
      }
    }
  }

  interface Balancer {
    Binding varType(PsiTypeVariable x, PsiType y);

    Binding varVar(PsiTypeVariable x, PsiTypeVariable y);

    Binding typeVar(PsiType x, PsiTypeVariable y);
  }

  interface Unifier {
    Binding unify(PsiType x, PsiType y);
  }

  public Binding balance(final PsiType x, final PsiType y, final Balancer balancer) {
    final int indicator = (x instanceof PsiTypeVariable ? 1 : 0) + (y instanceof PsiTypeVariable ? 2 : 0);

    switch (indicator) {
    case 0:
         if (x instanceof PsiWildcardType || y instanceof PsiWildcardType) {
           final PsiType xType = x instanceof PsiWildcardType ? ((PsiWildcardType)x).getBound() : x;
           final PsiType yType = y instanceof PsiWildcardType ? ((PsiWildcardType)y).getBound() : y;

           return balance(xType, yType, balancer);
         }
         else if (x instanceof PsiArrayType || y instanceof PsiArrayType) {
           final PsiType xType = x instanceof PsiArrayType ? ((PsiArrayType)x).getComponentType() : x;
           final PsiType yType = y instanceof PsiArrayType ? ((PsiArrayType)y).getComponentType() : y;

           return balance(xType, yType, balancer);
         }
         else if (x instanceof PsiClassType && y instanceof PsiClassType) {
           final PsiClassType.ClassResolveResult resultX = Util.resolveType(x);
           final PsiClassType.ClassResolveResult resultY = Util.resolveType(y);

           final PsiClass xClass = resultX.getElement();
           final PsiClass yClass = resultY.getElement();

           if (xClass != null && yClass != null) {
             final PsiSubstitutor ySubst = resultY.getSubstitutor();

             PsiSubstitutor xSubst = resultX.getSubstitutor();

             if (!xClass.equals(yClass)) {
               if (InheritanceUtil.isCorrectDescendant(xClass, yClass, true)) {
                 xSubst = TypeConversionUtil.getSuperClassSubstitutor(yClass, xClass, xSubst);
               }
               else {
                 return null;
               }
             }

             Binding b = create();

             for (Iterator<PsiTypeParameter> p = xSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
               final PsiTypeParameter aParm = p.next();
               final PsiType xType = xSubst.substitute(aParm);
               final PsiType yType = ySubst.substitute(aParm);

               final Binding b1 = unify(xType, yType, new Unifier() {
                                          public Binding unify(final PsiType x, final PsiType y) {
                                            return balance(x, y, balancer);
                                          }
                                        });

               if (b1 == null) {
                 return null;
               }

               b = b.compose(b1);
             }

             return b;
           }
         }
         else if (y instanceof Bottom) {
           return create();
         }
         else {
           return null;
         }

    case 1:
         return balancer.varType((PsiTypeVariable)x, y);

    case 2:
         return balancer.typeVar(x, (PsiTypeVariable)y);

    case 3:
         return balancer.varVar((PsiTypeVariable)x, (PsiTypeVariable)y);
    }

    return null;
  }

  private Binding unify(final PsiType x, final PsiType y, final Unifier unifier) {
    final int indicator = (x instanceof PsiTypeVariable ? 1 : 0) + (y instanceof PsiTypeVariable ? 2 : 0);

    switch (indicator) {
    case 0:
           if (x instanceof PsiWildcardType || y instanceof PsiWildcardType) {
             return unifier.unify(x, y);
           }
           else if (x instanceof PsiArrayType || y instanceof PsiArrayType) {
             final PsiType xType = x instanceof PsiArrayType ? ((PsiArrayType)x).getComponentType() : x;
             final PsiType yType = y instanceof PsiArrayType ? ((PsiArrayType)y).getComponentType() : y;

             return unify(xType, yType, unifier);
           }
           else if (x instanceof PsiClassType && y instanceof PsiClassType) {
             final PsiClassType.ClassResolveResult resultX = Util.resolveType(x);
             final PsiClassType.ClassResolveResult resultY = Util.resolveType(y);

             final PsiClass xClass = resultX.getElement();
             final PsiClass yClass = resultY.getElement();

             if (xClass != null && yClass != null) {
               final PsiSubstitutor ySubst = resultY.getSubstitutor();

               PsiSubstitutor xSubst = resultX.getSubstitutor();

               if (!xClass.equals(yClass)) {
                 return null;
               }

               Binding b = create();

               for (Iterator<PsiTypeParameter> p = xSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
                 final PsiTypeParameter aParm = p.next();
                 final PsiType xType = xSubst.substitute(aParm);
                 final PsiType yType = ySubst.substitute(aParm);

                 final Binding b1 = unify(xType, yType, unifier);

                 if (b1 == null) {
                   return null;
                 }

                 b = b.compose(b1);
               }

               return b;
             }
           }
           else if (y instanceof Bottom) {
             return create();
           }
           else {
             return null;
           }

    default:
           return unifier.unify(x, y);
    }
  }

  public Binding rise(final PsiType x, final PsiType y) {
    final Binding binding = balance(x, y, new Balancer() {
                                      public Binding varType(PsiTypeVariable x, PsiType y) {
                                        if (y instanceof Bottom) {
                                          return create();
                                        }

                                        return create(x, y);
                                        //== null || y instanceof PsiWildcardType
                                        //                ? y
                                        //                : PsiWildcardType.createExtends(PsiManager.getInstance(myProject), y));
                                      }

                                      public Binding varVar(PsiTypeVariable x, PsiTypeVariable y) {
                                        final int xi = x.getIndex();
                                        final int yi = y.getIndex();

                                        if (xi < yi) {
                                          return create(x, y);
                                        }
                                        else if (yi < xi) {
                                          return create(y, x);
                                        }
                                        else {
                                          return create();
                                        }
                                      }

                                      public Binding typeVar(PsiType x, PsiTypeVariable y) {
                                        if (x == null) {
                                          return create(y, Bottom.BOTTOM);
                                        }

                                        return create(y, x);
                                      }
                                    });

    return binding != null ? binding.reduceRecursive() : null;
  }

  public Binding sink(final PsiType x, final PsiType y) {
    return balance(x, y, new Balancer() {
                     public Binding varType(PsiTypeVariable x, PsiType y) {
                       return create(x, y);
                     }

                     public Binding varVar(PsiTypeVariable x, PsiTypeVariable y) {
                       return create(y, Bottom.BOTTOM);
                     }

                     public Binding typeVar(PsiType x, PsiTypeVariable y) {
                       return create(y, Bottom.BOTTOM);
                     }
                   });
  }

  public LinkedList<Pair<PsiType, Binding>> union(final PsiType x, final PsiType y) {
    final LinkedList<Pair<PsiType, Binding>> list = new LinkedList<Pair<PsiType, Binding>>();

    new Object() {
      void union(final PsiType x, final PsiType y, final LinkedList<Pair<PsiType, Binding>> list) {
        if (x instanceof PsiArrayType && y instanceof PsiArrayType) {
          union(((PsiArrayType)x).getComponentType(), ((PsiArrayType)y).getComponentType(), list);
        }
        else if (x instanceof PsiClassType && y instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult xResult = Util.resolveType(x);
          final PsiClassType.ClassResolveResult yResult = Util.resolveType(y);

          final PsiClass xClass = xResult.getElement();
          final PsiClass yClass = yResult.getElement();

          final PsiSubstitutor xSubst = xResult.getSubstitutor();
          final PsiSubstitutor ySubst = yResult.getSubstitutor();

          if (xClass == null || yClass == null) {
            return;
          }

          if (xClass.equals(yClass)) {
            final Binding risen = rise(x, y);

            if (risen == null) {
              return;
            }

            list.addFirst(new Pair<PsiType, Binding>(risen.apply(x), risen));
          }
          else {
            final PsiClass[] descendants = getGreatestLowerClasses(xClass, yClass);

            for (int i = 0; i < descendants.length; i++) {
              final PsiClass descendant = descendants[i];

              final PsiSubstitutor x2aSubst = TypeConversionUtil.getSuperClassSubstitutor(xClass, descendant, xSubst);
              final PsiSubstitutor y2aSubst = TypeConversionUtil.getSuperClassSubstitutor(yClass, descendant, ySubst);

              final PsiElementFactory factory = xClass.getManager().getElementFactory();

              union(factory.createType(descendant, x2aSubst), factory.createType(descendant, y2aSubst), list);
            }
          }
        }
      }
    }.union(x, y, list);

    return list;
  }

  public LinkedList<Pair<PsiType, Binding>> intersect(final PsiType x, final PsiType y) {
    final LinkedList<Pair<PsiType, Binding>> list = new LinkedList<Pair<PsiType, Binding>>();

    new Object() {
      void intersect(final PsiType x, final PsiType y, final LinkedList<Pair<PsiType, Binding>> list) {
        if (x instanceof PsiWildcardType || y instanceof PsiWildcardType) {
          final PsiType xType = x instanceof PsiWildcardType ? ((PsiWildcardType)x).getBound() : x;
          final PsiType yType = y instanceof PsiWildcardType ? ((PsiWildcardType)y).getBound() : y;

          intersect(xType, yType, list);
        }
        if (x instanceof PsiArrayType || y instanceof PsiArrayType) {
          if (x instanceof PsiClassType || y instanceof PsiClassType) {
            try {
              final PsiElementFactory f = PsiManager.getInstance(myProject).getElementFactory();
              final PsiType keyType = x instanceof PsiClassType ? x : y;

              final PsiType object = f.createTypeFromText("java.lang.Object", null);
              final PsiType cloneable = f.createTypeFromText("java.lang.Cloneable", null);
              final PsiType serializable = f.createTypeFromText("java.io.Serializable", null);

              intersect(keyType, object, list);
              intersect(keyType, cloneable, list);
              intersect(keyType, serializable, list);
            }
            catch (IncorrectOperationException e) {
              LOG.error("Exception " + e);
            }
          }
          else if (x instanceof PsiArrayType && y instanceof PsiArrayType) {
            intersect(((PsiArrayType)x).getComponentType(), ((PsiArrayType)y).getComponentType(), list);
          }
        }
        else if (x instanceof PsiClassType && y instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult xResult = Util.resolveType(x);
          final PsiClassType.ClassResolveResult yResult = Util.resolveType(y);

          final PsiClass xClass = xResult.getElement();
          final PsiClass yClass = yResult.getElement();

          final PsiSubstitutor xSubst = xResult.getSubstitutor();
          final PsiSubstitutor ySubst = yResult.getSubstitutor();

          if (xClass == null || yClass == null) {
            return;
          }

          if (xClass.equals(yClass)) {
            final Binding risen = rise(x, y);

            if (risen == null) {
              final PsiElementFactory factory = xClass.getManager().getElementFactory();

              list.addFirst(new Pair<PsiType, Binding>(Util.banalize(factory.createType(xClass, factory.createRawSubstitutor(xClass))),
                                                       create()));
            }
            else {
              list.addFirst(new Pair<PsiType, Binding>(risen.apply(x), risen));
            }
          }
          else {
            final PsiClass[] ancestors = GenericsUtil.getLeastUpperClasses(xClass, yClass);

            for (int i = 0; i < ancestors.length; i++) {
              final PsiClass ancestor = ancestors[i];

              if (ancestor.getQualifiedName().equals("java.lang.Object") && ancestors.length > 1) {
              continue;
              }

              final PsiSubstitutor x2aSubst = TypeConversionUtil.getSuperClassSubstitutor(ancestor, xClass, xSubst);
              final PsiSubstitutor y2aSubst = TypeConversionUtil.getSuperClassSubstitutor(ancestor, yClass, ySubst);

              final PsiElementFactory factory = xClass.getManager().getElementFactory();

              intersect(factory.createType(ancestor, x2aSubst), factory.createType(ancestor, y2aSubst), list);
            }
          }
        }
      }
    }.intersect(x, y, list);

    return list;
  }

  public BindingFactory(final com.intellij.refactoring.typeCook.deductive.builder.System system) {
    myVariablesNumber = system.getVariableFactory().getNumber();
    myBoundVariables = system.getBoundVariables();
    myProject = system.getProject();
    myFactory = system.getVariableFactory();

    final PsiTypeVariable[] index = myBoundVariables.toArray(new PsiTypeVariable[]{});

    myBoundVariableIndices = new int[index.length];

    for (int i = 0; i < index.length; i++) {
      myBoundVariableIndices[i] = index[i].getIndex();
    }

    Arrays.sort(myBoundVariableIndices);
  }

  public Binding create(final PsiTypeVariable var, final PsiType type) {
    return new BindingImpl(var, type);
  }

  public Binding create() {
    return new BindingImpl(myVariablesNumber);
  }

  private Binding create(final int index, final PsiType type) {
    return new BindingImpl(index, type);
  }

  public HashSet<PsiTypeVariable> getBoundVariables() {
    return myBoundVariables;
  }
}
