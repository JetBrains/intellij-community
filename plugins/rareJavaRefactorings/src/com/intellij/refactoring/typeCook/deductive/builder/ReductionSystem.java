// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class ReductionSystem {
  final Set<Constraint> myConstraints = new HashSet<>();
  final Set<PsiElement> myElements;
  final Map<PsiTypeCastExpression, PsiType> myCastToOperandType;
  final Map<PsiElement, PsiType> myTypes;
  final PsiTypeVariableFactory myTypeVariableFactory;
  final Project myProject;
  final Settings mySettings;

  Set<PsiTypeVariable> myBoundVariables;

  public ReductionSystem(final Project project,
                         final Set<PsiElement> elements,
                         final Map<PsiElement, PsiType> types,
                         final PsiTypeVariableFactory factory,
                         final Settings settings) {
    myProject = project;
    myElements = elements;
    myTypes = types;
    myTypeVariableFactory = factory;
    myBoundVariables = null;
    mySettings = settings;
    myCastToOperandType = new HashMap<>();
  }

  public Project getProject() {
    return myProject;
  }

  public Set<Constraint> getConstraints() {
    return myConstraints;
  }

  public void addCast(final PsiTypeCastExpression cast, final PsiType operandType){
    myCastToOperandType.put(cast, operandType);
  }

  public void addSubtypeConstraint(PsiType left, PsiType right) {
    if (left instanceof PsiPrimitiveType) left = ((PsiPrimitiveType)left).getBoxedType(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
    if (right instanceof PsiPrimitiveType) right = ((PsiPrimitiveType)right).getBoxedType(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
    if (left == null || right == null) {
      return;
    }

    if ((Util.bindsTypeVariables(left) || Util.bindsTypeVariables(right))
    ) {
      final Subtype c = new Subtype(left, right);
      myConstraints.add(c);
    }
  }

  private static String memberString(final PsiMember member) {
    return member.getContainingClass().getQualifiedName() + "." + member.getName();
  }

  private static String variableString(final PsiLocalVariable var) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);

    return memberString(method) + "#" + var.getName();
  }

  @Override
  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public String toString() {
    @NonNls StringBuilder buffer = new StringBuilder();

    buffer.append("Victims:\n");

    for (final PsiElement element : myElements) {
      final PsiType type = myTypes.get(element);

      if (type == null) {
        continue;
      }

      if (element instanceof PsiParameter param) {
        final PsiElement declarationScope = param.getDeclarationScope();
        if (declarationScope instanceof PsiMethod method) {
          buffer.append("   parameter " + method.getParameterList().getParameterIndex(param) + " of " + memberString(method));
        }
        else {
          buffer.append("   parameter of foreach");
        }
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
      for (final PsiTypeVariable boundVariable : myBoundVariables) {
        buffer.append(boundVariable.getIndex() + ", ");
      }
    }

    buffer.append("Constraints: " + myConstraints.size() + "\n");

    for (final Constraint constraint : myConstraints) {
      buffer.append("   " + constraint + "\n");
    }

    return buffer.toString();
  }

  public ReductionSystem[] isolate() {
    class Node {
      int myComponent = -1;
      final Constraint myConstraint;
      final Set<Node> myNeighbours = new HashSet<>();

      Node() {
        myConstraint = null;
      }

      Node(final Constraint c) {
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
    final Map<Constraint, Set<PsiTypeVariable>> boundVariables = new HashMap<>();

    for (int i = 0; i < typeVariableNodes.length; i++) {
      typeVariableNodes[i] = new Node();
    }

    {
      int j = 0;

      for (final Constraint constraint : myConstraints) {
        constraintNodes[j++] = new Node(constraint);
      }
    }

    {
      int l = 0;

      for (final Constraint constraint : myConstraints) {
        final Set<PsiTypeVariable> boundVars = new LinkedHashSet<>();
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

              for (final PsiType type : subst.getSubstitutionMap().values()) {
                visit(type);
              }
            }
            else if (t instanceof PsiIntersectionType) {
              final PsiType[] conjuncts = ((PsiIntersectionType)t).getConjuncts();
              for (PsiType conjunct : conjuncts) {
                visit(conjunct);

              }
            }
            else if (t instanceof PsiWildcardType) {
              final PsiType bound = ((PsiWildcardType)t).getBound();

              if (bound != null) {
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

    List<Set<PsiTypeVariable>> clusters = myTypeVariableFactory.getClusters();

    for (final Set<PsiTypeVariable> cluster : clusters) {
      Node prev = null;

      for (final PsiTypeVariable variable : cluster) {
        final Node curr = typeVariableNodes[variable.getIndex()];

        if (prev != null) {
          prev.addEdge(curr);
        }

        prev = curr;
      }
    }

    int currComponent = 0;

    for (final Node node : typeVariableNodes) {
      if (node.myComponent == -1) {
        final int component = currComponent;
        new Object() {
          void selectComponent(final Node n) {
            final LinkedList<Node> frontier = new LinkedList<>();

            frontier.addFirst(n);

            while (!frontier.isEmpty()) {
              final Node curr = frontier.removeFirst();

              curr.myComponent = component;

              for (final Node p : curr.myNeighbours) {
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

    final ReductionSystem[] systems = new ReductionSystem[currComponent];

    for (final Node node : constraintNodes) {
      final Constraint constraint = node.getConstraint();
      final int index = node.myComponent;

      if (systems[index] == null) {
        systems[index] = new ReductionSystem(myProject, myElements, myTypes, myTypeVariableFactory, mySettings);
      }

      systems[index].addConstraint(constraint, boundVariables.get(constraint));
    }

    return systems;
  }

  private void addConstraint(final Constraint constraint, final Set<PsiTypeVariable> vars) {
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

  public Set<PsiTypeVariable> getBoundVariables() {
    return myBoundVariables;
  }

  public @NonNls String dumpString() {
    final @NonNls String[] data = new String[myElements.size()];

    int i = 0;

    for (final PsiElement element : myElements) {
      data[i++] = Util.getType(element).getCanonicalText() + "\\n" + elementString(element);
    }

    return StreamEx.of(data).sorted().map(aData -> aData + "\n").joining();
  }

  private static @NonNls
  String elementString(final PsiElement element) {
    if (element instanceof PsiNewExpression) {
      return "new";
    }

    if (element instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)element).getDeclarationScope();

      if (scope instanceof PsiMethod method) {
        return "parameter " + (method.getParameterList().getParameterIndex(((PsiParameter)element))) + " of " + method.getName();
      }
    }

    if (element instanceof PsiMethod) {
      return "return of " + ((PsiMethod)element).getName();
    }

    return element.toString();
  }

  public String dumpResult(final Binding bestBinding) {
    final @NonNls String[] data = new String[myElements.size()];

    class Substitutor {
      PsiType substitute(final PsiType t) {
        if (t instanceof PsiWildcardType wcType) {
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

          for (final PsiTypeParameter parm : aSubst.getSubstitutionMap().keySet()) {
            final PsiType type = aSubst.substitute(parm);

            theSubst = theSubst.put(parm, substitute(type));
          }

          return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, theSubst);
        }
        else {
          return t;
        }
      }
    }

    final Substitutor binding = new Substitutor();
    int i = 0;

    for (final PsiElement element : myElements) {
      final PsiType t = myTypes.get(element);
      if (t != null) {
        data[i++] = binding.substitute(t).getCanonicalText() + "\\n" + elementString(element);
      }
      else {
        data[i++] = "\\n" + elementString(element);
      }
    }

    return StreamEx.of(data).sorted().map(aData -> aData + "\n").joining();
  }

  public Settings getSettings() {
    return mySettings;
  }
}
