package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.Bottom;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jan 13, 2005
 * Time: 3:46:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class BindingFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.resolver.BindingFactory");

  private PsiTypeVariableFactory myTypeVariableFactory;

  private class BindingImpl implements Binding {
    private PsiType[] myBindings;

    BindingImpl(final PsiTypeVariable var, final PsiType type) {
      myBindings = new PsiType[myTypeVariableFactory.getNumber()];

      myBindings[var.getIndex()] = type;
    }

    BindingImpl(final int n) {
      myBindings = new PsiType[n];
    }

    public PsiType apply(final PsiType type) {
      if (type instanceof PsiTypeVariable) {
        final PsiType t = myBindings[((PsiTypeVariable)type).getIndex()];
        return (t == null) ? type : t;
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
      else {
        return type;
      }
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
            b3.myBindings[i] = b2.apply(b1i);
            break;

          case 2: /* b2(i)\b1(i) */
            b3.myBindings[i] = b1.apply(b2i);
            break;

          case 3:  /* b2(i) \cap b1(i) */
            final Binding common = rise(b1i, b2i);

            if (common == null) {
              return null;
            }

            b3.myBindings[i] = b2.apply(common.apply(b1i));
        }
      }

      return b3;
    }

    public String toString() {
      final StringBuffer buffer = new StringBuffer();

      for (int i = 0; i < myBindings.length; i++) {
        final PsiType binding = myBindings[i];

        if (binding != null){
          buffer.append("#" + i + " -> " + binding.getPresentableText() + "; ");
        }
      }

      return buffer.toString();
    }
  }

  interface Balancer {
    Binding varType(PsiTypeVariable x, PsiType y);

    Binding varVar(PsiTypeVariable x, PsiTypeVariable y);

    Binding typeVar(PsiType x, PsiTypeVariable y);
  }

  public Binding balance(final PsiType x, final PsiType y, final Balancer balancer) {
    final int indicator = (x instanceof PsiTypeVariable ? 1 : 0) + (y instanceof PsiTypeVariable ? 2 : 0);

    switch (indicator) {
      case 0:
        if (x instanceof PsiArrayType && y instanceof PsiArrayType) {
          return balance(((PsiArrayType)x).getComponentType(), ((PsiArrayType)y).getComponentType(), balancer);
        }
        else if (x instanceof PsiClassType && y instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult resultX = Util.resolveType(x);
          final PsiClassType.ClassResolveResult resultY = Util.resolveType(y);

          final PsiClass xClass = resultX.getElement();
          final PsiClass yClass = resultY.getElement();

          if (xClass != null && yClass != null) {
            final PsiSubstitutor xSubst = resultX.getSubstitutor();

            PsiSubstitutor ySubst = resultY.getSubstitutor();

            if (!xClass.equals(yClass)) {
              if (InheritanceUtil.isCorrectDescendant(yClass, xClass, true)) {
                ySubst =
                Util.composeSubstitutors(ySubst, TypeConversionUtil.getSuperClassSubstitutor(xClass, yClass, PsiSubstitutor.EMPTY));
              }

              return null;
            }

            Binding b = create();

            for (Iterator<PsiTypeParameter> p = xSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
              final PsiTypeParameter aParm = p.next();
              final PsiType xType = xSubst.substitute(aParm);
              final PsiType yType = ySubst.substitute(aParm);

              final Binding b1 = balance(xType, yType, balancer);

              if (b1 == null) {
                return null;
              }

              b = b.compose(b1);
            }

            return b;
          }
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

  public Binding rise(final PsiType x, final PsiType y) {
    return balance(x, y, new Balancer() {

      public Binding varType(PsiTypeVariable x, PsiType y) {
        return create(x, y);
      }

      public Binding varVar(PsiTypeVariable x, PsiTypeVariable y) {
        final int xi = x.getIndex();
        final int yi = y.getIndex();

        if (xi < yi) {
          return create(((PsiTypeVariable)x), y);
        }
        else if (yi < xi) {
          return create(((PsiTypeVariable)y), x);
        }
        else {
          return create();
        }
      }

      public Binding typeVar(PsiType x, PsiTypeVariable y) {
        return create(y, x);
      }
    });
  }

  public Binding sink(final PsiType x, final PsiType y) {
    return balance(x, y, new Balancer() {

      public Binding varType(PsiTypeVariable x, PsiType y) {
        return create(x, Bottom.BOTTOM);
      }

      public Binding varVar(PsiTypeVariable x, PsiTypeVariable y) {
        return create(x, Bottom.BOTTOM);
      }

      public Binding typeVar(PsiType x, PsiTypeVariable y) {
        return create(y, x);
      }
    });
  }

  public BindingFactory(final PsiTypeVariableFactory typeVariableFactory) {
    myTypeVariableFactory = typeVariableFactory;
  }

  public Binding create(final PsiTypeVariable var, final PsiType type) {
    return new BindingImpl(var, type);
  }

  public Binding create() {
    return new BindingImpl(myTypeVariableFactory.getNumber());
  }
}
