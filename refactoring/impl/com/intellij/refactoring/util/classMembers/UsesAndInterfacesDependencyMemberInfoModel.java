/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 25.06.2002
 * Time: 14:01:08
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;

public class UsesAndInterfacesDependencyMemberInfoModel extends DelegatingMemberInfoModel {
  public static final InterfaceContainmentVerifier DEFAULT_CONTAINMENT_VERIFIER = new InterfaceContainmentVerifier() {
                      public boolean checkedInterfacesContain(PsiMethod psiMethod) {
                        return false;
                      }
                    };

  public UsesAndInterfacesDependencyMemberInfoModel(PsiClass aClass, PsiClass superClass, boolean recursive,
                                                    @NotNull final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    super(new ANDCombinedMemberInfoModel(
            new UsesDependencyMemberInfoModel(aClass, superClass, recursive) {
              public int checkForProblems(@NotNull MemberInfo memberInfo) {
                final int problem = super.checkForProblems(memberInfo);
                if (problem == OK) return OK;
                final PsiMember member = memberInfo.getMember();
                if (member instanceof PsiMethod) {
                  if (interfaceContainmentVerifier.checkedInterfacesContain((PsiMethod)member)) return OK;
                }
                return problem;
              }
            },
            new InterfaceDependencyMemberInfoModel(aClass))
    );
  }


  public void setSuperClass(PsiClass superClass) {
    ((UsesDependencyMemberInfoModel) ((ANDCombinedMemberInfoModel) getDelegatingTarget()).getModel1()).setSuperClass(superClass);
  }


}
