package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiTypeIntersection;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

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
  final HashMap<PsiElement, PsiType> myTypes;
  final PsiTypeVariableFactory myTypeVariableFactory;

  public System(final HashSet<PsiElement> elements, final HashMap<PsiElement, PsiType> types, final PsiTypeVariableFactory factory) {
    myElements = elements;
    myTypes = types;
    myTypeVariableFactory = factory;
  }

  public HashSet<Constraint> getConstraints() {
    return myConstraints;
  }

  private void addConstraint(final Constraint constraint) {
    myConstraints.add(constraint);
  }

  public void addSubtypeConstraint(final PsiType left, final PsiType right) {
    if ((Util.bindsTypeVariables(left) || Util.bindsTypeVariables(right)) &&
        !(left instanceof PsiPrimitiveType || right instanceof PsiPrimitiveType)
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
    buffer.append("Constraints: " + myConstraints.size() + "\n");

    for (Iterator<Constraint> i = myConstraints.iterator(); i.hasNext();) {
      buffer.append("   " + i.next() + "\n");
    }

    return buffer.toString();
  }

  public System[] isolate() {
    class Node {
      int myVar;
      int myComponent = -1;
      HashSet<Node> myNeighbours = new HashSet<Node>();

      public Node(int var) {
        myVar = var;
      }

      public void addEdge(final Node n) {
        if (!myNeighbours.contains(n)) {
          myNeighbours.add(n);
        }
      }

      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;

        final Node node = (Node)o;

        if (myVar != node.myVar) return false;

        return true;
      }

      public int hashCode() {
        return myVar;
      }
    }

    final Node[] nodes = new Node[myTypeVariableFactory.getNumber()];

    for (int i = 0; i < nodes.length; i++) {
      nodes[i] = new Node(i);
    }

    final HashMap<Constraint, Integer> constraintVar = new HashMap<Constraint, Integer>();

    for (Iterator<Constraint> i = myConstraints.iterator(); i.hasNext();) {
      final HashSet<Integer> boundVariables = new HashSet<Integer>();

      new Object () {
        private Constraint myCurrent;

        void visit(final Constraint c){
          myCurrent = c;

          visit(c.getLeft());
          visit(c.getRight());
        }

        private void visit(final PsiType t) {
          if (t instanceof PsiTypeVariable) {
            final Integer index = new Integer(((PsiTypeVariable)t).getIndex());

            boundVariables.add(index);

            if (constraintVar.get(myCurrent) == null){
              constraintVar.put(myCurrent, index);
            }
          }
          else if (t instanceof PsiArrayType) {
            visit(((PsiArrayType)t).getDeepComponentType());
          }
          else if (t instanceof PsiClassType) {
            final PsiSubstitutor subst = Util.resolveType(t).getSubstitutor();

            for (Iterator<PsiType> j = subst.getSubstitutionMap().values().iterator(); j.hasNext();) {
              visit(j.next());
            }
          }
          else if (t instanceof PsiTypeIntersection){
            visit (((PsiTypeIntersection)t).getLeft());
            visit (((PsiTypeIntersection)t).getRight());
          }
        }
      }.visit (i.next ());

      final Integer[] bound = boundVariables.toArray(new Integer[]{});

      for (int j = 0; j < bound.length; j++) {
        final int x = bound[j].intValue();

        for (int k = j + 1; k < bound.length; k++) {
          final int y = bound[k].intValue();

          nodes[x].addEdge(nodes[y]);
        }
      }
    }

    int currComponent = 0;

    for (int i = 0; i < nodes.length; i++) {
      final Node node = nodes[i];

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

    for (Iterator<Constraint> c = myConstraints.iterator(); c.hasNext();){
      final Constraint constraint = c.next();
      final Integer variable = constraintVar.get(constraint);
      final int index = nodes[variable.intValue()].myComponent;

      if (systems[index] == null){
        systems[index] = new System(myElements, myTypes, myTypeVariableFactory);
      }

      systems[index].addConstraint(constraint);
    }

    return systems;
  }

  public PsiTypeVariableFactory getVariableFactory() {
    return myTypeVariableFactory;
  }
}
