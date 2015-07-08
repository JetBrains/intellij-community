/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.Processor;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;

/**
 * @author yole
 */
public class ListenerNavigateButton extends JButton implements ActionListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.ListenerNavigateButton");

  private final RadComponent myComponent;

  public ListenerNavigateButton(RadComponent component) {
    myComponent = component;
    setIcon(UIDesignerIcons.Listener);
    setOpaque(false);
    setFocusable(false);
    setBorderPainted(false);
    setSize(new Dimension(getIcon().getIconWidth(), getIcon().getIconHeight()));
    addActionListener(this);
  }

  public void actionPerformed(ActionEvent e) {
    showNavigatePopup(myComponent, false);
  }

  public static void showNavigatePopup(final RadComponent component, final boolean showIfEmpty) {
    final DefaultActionGroup actionGroup = prepareActionGroup(component);
    if (actionGroup != null && actionGroup.getChildrenCount() == 0 && showIfEmpty) {
      actionGroup.add(new MyNavigateAction(UIDesignerBundle.message("navigate.to.listener.empty"), null));
    }
    if (actionGroup != null && actionGroup.getChildrenCount() > 0) {
      final DataContext context = DataManager.getInstance().getDataContext(component.getDelegee());
      final JBPopupFactory factory = JBPopupFactory.getInstance();
      final ListPopup popup = factory.createActionGroupPopup(UIDesignerBundle.message("navigate.to.listener.title"), actionGroup, context,
                                                             JBPopupFactory.ActionSelectionAid.NUMBERING, true);
      FormEditingUtil.showPopupUnderComponent(popup, component);
    }
  }

  @Nullable
  public static DefaultActionGroup prepareActionGroup(final RadComponent component) {
    final IRootContainer root = FormEditingUtil.getRoot(component);
    final String classToBind = root == null ? null : root.getClassToBind();
    if (classToBind != null) {
      final PsiClass aClass = FormEditingUtil.findClassToBind(component.getModule(), classToBind);
      if (aClass != null) {
        final PsiField boundField = aClass.findFieldByName(component.getBinding(), false);
        if (boundField != null) {
          return buildNavigateActionGroup(component, boundField);
        }
      }
    }
    return null;
  }

  private static DefaultActionGroup buildNavigateActionGroup(RadComponent component, final PsiField boundField) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final EventSetDescriptor[] eventSetDescriptors;
    try {
      BeanInfo beanInfo = Introspector.getBeanInfo(component.getComponentClass());
      eventSetDescriptors = beanInfo.getEventSetDescriptors();
    }
    catch (IntrospectionException e) {
      LOG.error(e);
      return null;
    }
    PsiFile boundClassFile = boundField.getContainingFile();
    if (boundClassFile == null) {
      return null;
    }
    final LocalSearchScope scope = new LocalSearchScope(boundClassFile);
    ReferencesSearch.search(boundField, scope).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference ref) {
        final PsiElement element = ref.getElement();
        if (element.getParent() instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression) element.getParent();
          if (refExpr.getParent() instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) refExpr.getParent();
            final PsiElement psiElement = refExpr.resolve();
            if (psiElement instanceof PsiMethod) {
              PsiMethod method = (PsiMethod) psiElement;
              for(EventSetDescriptor eventSetDescriptor: eventSetDescriptors) {
                if (Comparing.equal(eventSetDescriptor.getAddListenerMethod().getName(), method.getName())) {
                  final String eventName = eventSetDescriptor.getName();
                  final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                  if (args.length > 0) {
                    addListenerRef(actionGroup, eventName, args[0]);
                  }
                }
              }
            }
          }
        }
        return true;
      }
    });

    return actionGroup;
  }

  private static void addListenerRef(final DefaultActionGroup actionGroup, final String eventName, final PsiExpression listenerArg) {
    final PsiType type = listenerArg.getType();
    if (type instanceof PsiClassType) {
      PsiClass listenerClass = ((PsiClassType) type).resolve();
      if (listenerClass != null) {
        if (!isAbstractOrInterface(listenerClass)) {
          actionGroup.add(new MyNavigateAction(eventName + ": " + ClassPresentationUtil.getNameForClass(listenerClass, false),
                                               listenerClass));
          return;
        }
        else if (listenerArg instanceof PsiReferenceExpression) {
          final PsiElement psiElement = ((PsiReferenceExpression)listenerArg).resolve();
          if (psiElement instanceof PsiVariable) {
            PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(listenerArg, PsiCodeBlock.class);
            final PsiElement[] defs = DefUseUtil.getDefs(codeBlock, (PsiVariable)psiElement, listenerArg);
            if (defs.length == 1) {
              final PsiElement def = defs[0];
              if (def instanceof PsiVariable) {
                PsiVariable var = (PsiVariable) def;
                if (var.getInitializer() != listenerArg) {
                  addListenerRef(actionGroup, eventName, var.getInitializer());
                  return;
                }
              }
              else if (def.getParent() instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpr = (PsiAssignmentExpression)def.getParent();
                if (def.equals(assignmentExpr.getLExpression())) {
                  addListenerRef(actionGroup, eventName, assignmentExpr.getRExpression());
                  return;
                }
              }
            }
          }
        }
      }
    }
    actionGroup.add(new MyNavigateAction(eventName + ": " + listenerArg.getText(), listenerArg));
  }

  private static boolean isAbstractOrInterface(final PsiClass element) {
    return element.isInterface() ||
           element.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  private static class MyNavigateAction extends AnAction {
    private final PsiElement myElement;

    public MyNavigateAction(final String name, PsiElement element) {
      super(name);
      myElement = element;
    }

    public void actionPerformed(AnActionEvent e) {
      if (myElement instanceof Navigatable) {
        ((Navigatable) myElement).navigate(true);
      }
    }

    @Override public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myElement != null &&
                                     (!(myElement instanceof PsiClass) || !isAbstractOrInterface((PsiClass)myElement)));
    }
  }
}
