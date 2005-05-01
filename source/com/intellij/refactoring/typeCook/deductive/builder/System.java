package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.psi.Bottom;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.psi.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.openapi.project.Project;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jul 20, 2004
 * Time: 6:03:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class System {
  final HashSet<Constraint> myConstraints = new HashSet<Constraint>();
  final HashSet<PsiElement> myElements;
  final HashSet<PsiTypeCastExpression> myCasts;
  final HashMap<PsiElement, PsiType> myTypes;
  final PsiTypeVariableFactory myTypeVariableFactory;
  final Project myProject;
  final Settings mySettings;

  HashSet<PsiTypeVariable> myBoundVariables;

  public System(final Project project,
                final HashSet<PsiElement> elements,
                final HashMap<PsiElement, PsiType> types,
                final PsiTypeVariableFactory factory,
                final Settings settings) {
    myProject = project;
    myElements = elements;
    myTypes = types;
    myTypeVariableFactory = factory;
    myBoundVariables = null;
    mySettings = settings;
    myCasts = new HashSet<PsiTypeCastExpression> ();
  }

  public Project getProject() {
    return myProject;
  }

  public HashSet<Constraint> getConstraints() {
    return myConstraints;
  }

  public void addCast (final PsiTypeCastExpression cast){
    myCasts.add(cast);
  }

  public void addSubtypeConstraint(final PsiType left, final PsiType right) {
    if (left == null || right == null) {
      return;
    }

    if ((Util.bindsTypeVariables(left) || Util.bindsTypeVariables(right)) &&
                                                                          !(left instanceof PsiPrimitiveType ||
                                                                             right instanceof PsiPrimitiveType)
    ) {
      final Subtype c = new Subtype(left, right);
      if (!myConstraints.contains(c)) {
        myConstraints.add(c);
      }
    }
  }

  private String memberString(final PsiMember member) {
    return member.getContainingClass().getQualifiedName() + "." + ((PsiNamedElement)member).getName();
  }

  private String variableString(final PsiLocalVariable var) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);

    return memberString(method) + "#" + var.getName();
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();

    buffer.append("Victims:\n");

    for (Iterator<PsiElement> i = myElements.iterator(); i.hasNext();) {
      final PsiElement element = i.next();
      final PsiType type = myTypes.get(element);

      if (type == null) {
      continue;
      }

      if (element instanceof PsiParameter) {
        final PsiParameter parm = (PsiParameter)element;
        final PsiMethod method = (PsiMethod)parm.getDeclarationScope();

        buffer.append("   parameter " + method.getParameterList().getParameterIndex(parm) + " of " + memberString(method));
      }
      else if (element instanceof PsiField) {
        buffer.append("   field " + memberString(((PsiField)element)));
      }
      else if (element instanceof PsiLocalVariable) {
        buffer.append("   local " + variableString(((PsiLocalVariable)element)));
      }
      else if (element instanceof PsiMethod) {
        buffer.append("   return of " + memberString(((PsiMethod)element)));
      }
      else if (element instanceof PsiNewExpression) {
        buffer.append("   " + element.getText());
      }
      else if (element instanceof PsiTypeCastExpression) {
        buffer.append("   " + element.getText());
      }
      else {
        buffer.append("   unknown: " + (element == null ? "null" : element.getClass().getName()));
      }

      buffer.append(" " + type.getCanonicalText() + "\n");
    }

    buffer.append("Variables: " + myTypeVariableFactory.getNumber() + "\n");
    buffer.append("Bound variables: ");

    if (myBoundVariables == null) {
      buffer.append(" not specified\n");
    }
    else {
      for (Iterator<PsiTypeVariable> i = myBoundVariables.iterator(); i.hasNext();) {
        buffer.append(i.next().getIndex() + ", ");
      }
    }

    buffer.append("Constraints: " + myConstraints.size() + "\n");

    for (Iterator<Constraint> i = myConstraints.iterator(); i.hasNext();) {
      buffer.append("   " + i.next() + "\n");
    }

    return buffer.toString();
  }

  public System[] isolate() {
    class Node {
      int myComponent = -1;
      Constraint myConstraint;
      HashSet<Node> myNeighbours = new HashSet<Node>();

      public Node() {
        myConstraint = null;
      }

      public Node(final Constraint c) {
        myConstraint = c;
      }

      public Constraint getConstraint() {
        return myConstraint;
      }

      public void addEdge(final Node n) {
        if (!myNeighbours.contains(n)) {
          myNeighbours.add(n);
          n.addEdge(this);
        }
      }
    }

    final Node[] typeVariableNodes = new Node[myTypeVariableFactory.getNumber()];
    final Node[] constraintNodes = new Node[myConstraints.size()];
    final HashMap<Constraint, HashSet<PsiTypeVariable>> boundVariables = new HashMap<Constraint, HashSet<PsiTypeVariable>>();

    for (int i = 0; i < typeVariableNodes.length; i++) {
      typeVariableNodes[i] = new Node();
    }

    {
      int j = 0;

      for (Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();) {
        constraintNodes[j++] = new Node(c.next());
      }
    }

    {
      int l = 0;

      for (Iterator<Constraint> i = myConstraints.iterator(); i.hasNext();) {
        final HashSet<PsiTypeVariable> boundVars = new HashSet<PsiTypeVariable>();

        final Constraint constraint = i.next();
        final Node constraintNode = constraintNodes[l++];

        new Object() {
          void visit(final Constraint c) {
            visit(c.getLeft());
            visit(c.getRight());
          }

          private void visit(final PsiType t) {
            if (t instanceof PsiTypeVariable) {
              boundVars.add((PsiTypeVariable)t);
            }
            else if (t instanceof PsiArrayType) {
              visit(t.getDeepComponentType());
            }
            else if (t instanceof PsiClassType) {
              final PsiSubstitutor subst = Util.resolveType(t).getSubstitutor();

              for (Iterator<PsiType> j = subst.getSubstitutionMap().values().iterator(); j.hasNext();) {
                visit(j.next());
              }
            }
            else if (t instanceof PsiIntersectionType) {
              final PsiType[] conjuncts = ((PsiIntersectionType)t).getConjuncts();
              for (int j = 0; j < conjuncts.length; j++) {
                visit(conjuncts[j]);

              }
            }
            else if (t instanceof PsiWildcardType){
              final PsiType bound = ((PsiWildcardType)t).getBound();

              if (bound != null){
                visit(bound);
              }
            }
          }
        }.visit(constraint);

        final PsiTypeVariable[] bound = boundVars.toArray(new PsiTypeVariable[]{});

        for (int j = 0; j < bound.length; j++) {
          final int x = bound[j].getIndex();
          final Node typeVariableNode = typeVariableNodes[x];

          typeVariableNode.addEdge(constraintNode);

          for (int k = j + 1; k < bound.length; k++) {
            final int y = bound[k].getIndex();

            typeVariableNode.addEdge(typeVariableNodes[y]);
          }
        }

        boundVariables.put(constraint, boundVars);
      }
    }

    final LinkedList<HashSet<PsiTypeVariable>> clusters = myTypeVariableFactory.getClusters();

    for (final Iterator<HashSet<PsiTypeVariable>> c = clusters.iterator(); c.hasNext();) {
      final HashSet<PsiTypeVariable> cluster = c.next();
      Node prev = null;

      for (final Iterator<PsiTypeVariable> v = cluster.iterator(); v.hasNext();) {
        final Node curr = typeVariableNodes[v.next().getIndex()];

        if (prev != null) {
          prev.addEdge(curr);
        }

        prev = curr;
      }
    }

    int currComponent = 0;

    for (int i = 0; i < typeVariableNodes.length; i++) {
      final Node node = typeVariableNodes[i];

      if (node.myComponent == -1) {
        final int component = currComponent;
        new Object() {
          void selectComponent(final Node n) {
            final LinkedList<Node> frontier = new LinkedList<Node>();

            frontier.addFirst(n);

            while (frontier.size() > 0) {
              final Node curr = frontier.removeFirst();

              curr.myComponent = component;

              for (Iterator<Node> i = curr.myNeighbours.iterator(); i.hasNext();) {
                final Node p = i.next();

                if (p.myComponent == -1) {
                  frontier.addFirst(p);
                }
              }
            }
          }
        }.selectComponent(node);

        currComponent++;
      }
    }

    final System[] systems = new System[currComponent];

    for (int i = 0; i < constraintNodes.length; i++) {
      final Node node = constraintNodes[i];
      final Constraint constraint = node.getConstraint();
      final int index = node.myComponent;

      if (systems[index] == null) {
        systems[index] = new System(myProject, myElements, myTypes, myTypeVariableFactory, mySettings);
      }

      systems[index].addConstraint(constraint, boundVariables.get(constraint));
    }

    return systems;
  }

  private void addConstraint(final Constraint constraint, final HashSet<PsiTypeVariable> vars) {
    if (myBoundVariables == null) {
      myBoundVariables = vars;
    }
    else {
      myBoundVariables.addAll(vars);
    }

    myConstraints.add(constraint);
  }

  public PsiTypeVariableFactory getVariableFactory() {
    return myTypeVariableFactory;
  }

  public HashSet<PsiTypeVariable> getBoundVariables() {
    return myBoundVariables;
  }

  public String dumpString() {
    final String[] data = new String[myElements.size()];

    int i = 0;

    for (final Iterator<PsiElement> e = myElements.iterator(); e.hasNext();) {
      final PsiElement element = e.next();
      data[i++] = Util.getType(element).getCanonicalText() + "\\n" + elementString(element);
    }

    Arrays.sort(data,
                new Comparator() {
                  public int compare(Object x, Object y) {
                    return ((String)x).compareTo((String)y);
                  }
                });


    final StringBuffer repr = new StringBuffer();

    for (int j = 0; j < data.length; j++) {
      repr.append(data[j]);
      repr.append("\n");
    }

    return repr.toString();
  }

  private String elementString(final PsiElement element) {
    if (element instanceof PsiNewExpression) {
      return "new";
    }

    if (element instanceof PsiParameter) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

      if (method != null) {
        return "parameter " + (method.getParameterList().getParameterIndex(((PsiParameter)element))) + " of " + method.getName();
      }
    }

    if (element instanceof PsiMethod) {
      return "return of " + ((PsiMethod)element).getName();
    }

    return element.toString();
  }

  public String dumpResult(final Binding bestBinding) {
    final String[] data = new String[myElements.size()];

    class Substitutor {
      PsiType substitute(final PsiType t) {
        if (t instanceof PsiWildcardType) {
          final PsiWildcardType wcType = (PsiWildcardType)t;
          final PsiType bound = wcType.getBound();

          if (bound == null) {
            return t;
          }

          final PsiManager manager = PsiManager.getInstance(myProject);
          final PsiType subst = substitute(bound);
          return subst == null || subst instanceof PsiWildcardType ? subst : wcType.isExtends()
                                                                             ? PsiWildcardType.createExtends(manager, subst)
                                                                             : PsiWildcardType.createSuper(manager, subst);
        }
        else if (t instanceof PsiTypeVariable) {
          if (bestBinding != null) {
            final PsiType b = bestBinding.apply(t);

            if (b instanceof Bottom || b instanceof PsiTypeVariable) {
              return null;
            }

            return substitute(b);
          }

          return null;
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

    final Substitutor binding = new Substitutor();
    int i = 0;

    for (final Iterator<PsiElement> e = myElements.iterator(); e.hasNext();) {
      final PsiElement element = e.next();
      final PsiType t = myTypes.get(element);
      if (t != null) {
        data[i++] = binding.substitute(t).getCanonicalText() + "\\n" + elementString(element);
      }
      else {
        data[i++] = "\\n" + elementString(element);
      }
    }

    Arrays.sort(data,
                new Comparator() {
                  public int compare(Object x, Object y) {
                    return ((String)x).compareTo((String)y);
                  }
                });


    final StringBuffer repr = new StringBuffer();

    for (int j = 0; j < data.length; j++) {
      repr.append(data[j]);
      repr.append("\n");
    }

    return repr.toString();
  }

  public Settings getSettings() {
    return mySettings;
  }
}
