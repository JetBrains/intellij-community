/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.AddSelfQuickFix;
import com.jetbrains.python.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Looks for the 'self' or its equivalents.
 * @author dcheryasov
 */
public class PyMethodParametersInspection extends PyInspection {
  public String MCS = "mcs";
  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    ComboBox comboBox = new ComboBox(new String[] {"mcs", "metacls"});
    comboBox.setSelectedItem(MCS);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ComboBox cb = (ComboBox)e.getSource();
        MCS = (String)cb.getSelectedItem();
      }
    });

    JPanel option = new JPanel(new BorderLayout());
    option.add(new JLabel("Metaclass method first argument name"), BorderLayout.WEST);
    option.add(comboBox, BorderLayout.EAST);

    final JPanel root = new JPanel(new BorderLayout());
    root.add(option, BorderLayout.PAGE_START);
    return root;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.problematic.first.parameter");
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WEAK_WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public class Visitor extends PyInspectionVisitor {
    private Ref<PsiElement> myPossibleZopeRef = null;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Nullable
    private PsiElement findZopeInterface(PsiElement foothold) {
      PsiElement ret;
      synchronized (this) { // other threads would wait as long in resolveInRoots() anyway
        if (myPossibleZopeRef == null) {
          myPossibleZopeRef = new Ref<>();
          ret = ResolveImportUtil.resolveModuleInRoots(QualifiedName.fromComponents("zope.interface.Interface"), foothold);
          myPossibleZopeRef.set(ret); // null is OK
        }
        else ret = myPossibleZopeRef.get();
      }
      return ret;
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
        if (extension.ignoreMethodParameters(node)) {
          return;
        }
      }
      // maybe it's a zope interface?
      PsiElement zope_interface = findZopeInterface(node);
      final PyClass cls = node.getContainingClass();
      if (zope_interface instanceof PyClass) {
        if (cls != null && cls.isSubclass((PyClass) zope_interface, myTypeEvalContext)) return; // it can have any params
      }
      // analyze function itself
      PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(node);
      if (flags != null) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        final String methodName = node.getName();
        final String CLS = "cls"; // TODO: move to style settings
        if (params.length == 0) { // fix: add
          // check for "staticmetod"
          if (flags.isStaticMethod()) return; // no params may be fine
          // check actual param list
          ASTNode name_node = node.getNameNode();
          if (name_node != null) {
            PsiElement open_paren = plist.getFirstChild();
            PsiElement close_paren = plist.getLastChild();
            if (
              open_paren != null && close_paren != null &&
              "(".equals(open_paren.getText()) && ")".equals(close_paren.getText())
            ) {
              String paramName;
              if (flags.isMetaclassMethod()) {
                if (flags.isClassMethod()) {
                  paramName = MCS;
                }
                else {
                  paramName = CLS;
                }
              }
              else if (flags.isClassMethod()) {
                paramName = CLS;
              }
              else {
                paramName = PyNames.CANONICAL_SELF;
              }
              registerProblem(
                plist, PyBundle.message("INSP.must.have.first.parameter", paramName),
                ProblemHighlightType.GENERIC_ERROR, null, new AddSelfQuickFix(paramName)
              );
            }
          }
        }
        else { // fix: rename
          PyNamedParameter first_param = params[0].getAsNamed();
          if (first_param != null) {
            String pname = first_param.getName();
            if (pname == null) {
              return;
            }
            // every dup, swap, drop, or dup+drop of "self"
            @NonNls String[] mangled = {"eslf", "sself", "elf", "felf", "slef", "seelf", "slf", "sslf", "sefl", "sellf", "sef", "seef"};
            if (PyUtil.among(pname, mangled)) {
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyBundle.message("INSP.probably.mistyped.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
              return;
            }
            if (flags.isMetaclassMethod()) {
              if (flags.isStaticMethod() && !PyNames.NEW.equals(methodName)) {
                return;
              }
              String expectedName;
              String alternativeName = null;
              if (PyNames.NEW.equals(methodName) || flags.isClassMethod()) {
                expectedName = MCS;
              }
              else if (flags.isSpecialMetaclassMethod()) {
                expectedName = CLS;
              }
              else {
                expectedName = PyNames.CANONICAL_SELF;
                alternativeName = CLS;
              }
              if (!expectedName.equals(pname) && (alternativeName == null || !alternativeName.equals(pname))) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.$0", expectedName),
                  new RenameParameterQuickFix(expectedName)
                );
              }
            }
            else if (flags.isClassMethod() || PyNames.NEW.equals(methodName)) {
              if (!CLS.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.$0", CLS),
                  new RenameParameterQuickFix(CLS)
                );
              }
            }
            else if (!flags.isStaticMethod() && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname)) {
              if (flags.isMetaclassMethod() && CLS.equals(pname)) {
                return;   // accept either 'self' or 'cls' for all methods in metaclass
              }
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyBundle.message("INSP.usually.named.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
            }
          }
          else { // the unusual case of a method with first tuple param
            if (!flags.isStaticMethod()) {
              registerProblem(plist, PyBundle.message("INSP.first.param.must.not.be.tuple"));
            }
          }
        }
      }
    }
  }

}
