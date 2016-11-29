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
package com.jetbrains.rest.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.psi.*;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestDirectiveBlock;
import com.jetbrains.rest.psi.RestRole;
import com.jetbrains.rest.quickfixes.AddIgnoredRoleFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 *
 * Looks for using not defined roles
 */
public class RestRoleInspection extends RestInspection {
  public JDOMExternalizableStringList ignoredRoles = new JDOMExternalizableStringList();
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return RestBundle.message("INSP.role.not.defined");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder, ignoredRoles);
  }

  private class Visitor extends RestInspectionVisitor {
    private final ImmutableSet<String> myIgnoredRoles;
    Set<String> mySphinxRoles = new HashSet<>();

    public Visitor(final ProblemsHolder holder, List<String> ignoredRoles) {
      super(holder);
      myIgnoredRoles = ImmutableSet.copyOf(ignoredRoles);
      Project project = holder.getProject();
      final Module module = ModuleUtilCore.findModuleForPsiElement(holder.getFile());
      if (module == null) return;
      String dir = ReSTService.getInstance(module).getWorkdir();
      if (!dir.isEmpty())
        fillSphinxRoles(dir, project);
    }

    private void fillSphinxRoles(String dir, Project project) {
      VirtualFile config = LocalFileSystem.getInstance().findFileByPath((dir.endsWith("/")?dir:dir+"/")+"conf.py");
      if (config == null) return;

      PsiFile configFile = PsiManager.getInstance(project).findFile(config);
      if (configFile instanceof PyFile) {
        PyFile file = (PyFile)configFile;
        List<PyFunction> functions = file.getTopLevelFunctions();
        for (PyFunction function : functions) {
          if (!"setup".equals(function.getName())) continue;
          PyStatementList stList = function.getStatementList();
          PyStatement[] statements = stList.getStatements();
          for (PyElement statement : statements) {
            if (statement instanceof PyExpressionStatement)
              statement = ((PyExpressionStatement)statement).getExpression();
            if (statement instanceof PyCallExpression) {
              if (((PyCallExpression)statement).isCalleeText("add_role")) {
                PyExpression arg = ((PyCallExpression)statement).getArguments()[0];
                if (arg instanceof PyStringLiteralExpression)
                  mySphinxRoles.add(((PyStringLiteralExpression)arg).getStringValue());
              }
            }
          }
        }
      }
    }

    @Override
    public void visitRole(final RestRole node) {
      RestFile file = (RestFile)node.getContainingFile();

      if (PsiTreeUtil.getParentOfType(node, RestDirectiveBlock.class) != null) return;
      final PsiElement sibling = node.getNextSibling();
      if (sibling == null || sibling.getNode().getElementType() != RestTokenTypes.INTERPRETED) return;
      if (RestUtil.PREDEFINED_ROLES.contains(node.getText()) || myIgnoredRoles.contains(node.getRoleName()))
        return;

      if (RestUtil.SPHINX_ROLES.contains(node.getText()) || RestUtil.SPHINX_ROLES.contains(":py"+node.getText())
          || mySphinxRoles.contains(node.getRoleName())) return;

      Set<String> definedRoles = new HashSet<>();

      RestDirectiveBlock[] directives = PsiTreeUtil.getChildrenOfType(file, RestDirectiveBlock.class);
      if (directives != null) {
        for (RestDirectiveBlock block : directives) {
          if (block.getDirectiveName().equals("role::")) {
            PsiElement role = block.getFirstChild().getNextSibling();
            if (role != null) {
              String roleName = role.getText().trim();
              int index = roleName.indexOf('(');
              if (index != -1)
                roleName = roleName.substring(0, index);
              definedRoles.add(roleName);
            }
          }
        }
      }
      if (definedRoles.contains(node.getRoleName())) return;
      registerProblem(node, "Not defined role '" + node.getRoleName() + "'", new AddIgnoredRoleFix(node.getRoleName(), RestRoleInspection.this));
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    ListEditForm form = new ListEditForm("Ignore roles", ignoredRoles);
    return form.getContentPanel();
  }
}
