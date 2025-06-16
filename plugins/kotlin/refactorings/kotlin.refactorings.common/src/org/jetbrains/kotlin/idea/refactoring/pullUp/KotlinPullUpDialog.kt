// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoChange
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.memberPullUp.PullUpDialogBase
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.isCompanionMemberOf
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.refactoring.memberInfo.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.awt.event.ItemEvent
import javax.swing.JComboBox

@ApiStatus.Internal
class KotlinPullUpDialog(
  project: Project,
  classOrObject: KtClassOrObject,
  superClasses: List<PsiNamedElement>,
  memberInfoStorage: KotlinMemberInfoStorage,
) : PullUpDialogBase<KotlinMemberInfoStorage, KotlinMemberInfo, KtNamedDeclaration, PsiNamedElement>(
    project, classOrObject, superClasses, memberInfoStorage, RefactoringBundle.message("pull.members.up.title")
) {
    init {
        init()
    }

    private inner class MemberInfoModelImpl(
      originalClass: KtClassOrObject,
      superClass: PsiNamedElement?,
      interfaceContainmentVerifier: (KtNamedDeclaration) -> Boolean
    ) : KotlinUsesAndInterfacesDependencyMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>(
        originalClass,
        superClass,
        false,
        interfaceContainmentVerifier
    ) {
        private var lastSuperClass: PsiNamedElement? = null

        private fun KtNamedDeclaration.isConstructorParameterWithInterfaceTarget(targetClass: PsiNamedElement): Boolean {
            return targetClass is KtClass && targetClass.isInterface() && isConstructorDeclaredProperty()
        }

        // Abstract members remain abstract
        override fun isFixedAbstract(memberInfo: KotlinMemberInfo?) = true

        /*
         * Any non-abstract function can change abstractness.
         *
         * Non-abstract property with initializer or delegate is always made abstract.
         * Any other non-abstract property can change abstractness.
         *
         * Classes do not have abstractness
         */
        override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
            val superClass = superClass ?: return false
            if (superClass is PsiClass) return false
            if (superClass !is KtClass) return false

            val member = memberInfo.member
            if (member.hasModifier(KtTokens.INLINE_KEYWORD) ||
                member.hasModifier(KtTokens.EXTERNAL_KEYWORD) ||
                member.hasModifier(KtTokens.LATEINIT_KEYWORD)
            ) return false
            if (member.isAbstractInInterface(sourceClass)) return false
            if (member.isConstructorParameterWithInterfaceTarget(superClass)) return false
            if (member.isCompanionMemberOf(sourceClass)) return false

            if (!superClass.isInterface()) return true

            return member is KtNamedFunction || (member is KtProperty && !member.mustBeAbstractInInterface()) || member is KtParameter
        }

        override fun isAbstractWhenDisabled(memberInfo: KotlinMemberInfo): Boolean {
            val superClass = superClass
            val member = memberInfo.member
            if (member.isCompanionMemberOf(sourceClass)) return false
            if (member.isAbstractInInterface(sourceClass)) return true
            if (superClass != null && member.isConstructorParameterWithInterfaceTarget(superClass)) return true
            return ((member is KtProperty || member is KtParameter) && superClass !is PsiClass)
                    || (member is KtNamedFunction && superClass is PsiClass)
        }

        override fun isMemberEnabled(memberInfo: KotlinMemberInfo): Boolean {
            val superClass = superClass ?: return false
            val member = memberInfo.member

            if (member.hasModifier(KtTokens.CONST_KEYWORD)) return false

            if (superClass is KtClass && superClass.isInterface() &&
                (member.hasModifier(KtTokens.INTERNAL_KEYWORD) || member.hasModifier(KtTokens.PROTECTED_KEYWORD))
            ) return false

            if (superClass is PsiClass) {
                if (!member.canMoveMemberToJavaClass(superClass)) return false
                if (member.isCompanionMemberOf(sourceClass)) return false
            }
            if (memberInfo in memberInfoStorage.getDuplicatedMemberInfos(superClass)) return false
            if (member in memberInfoStorage.getExtending(superClass)) return false
            return true
        }

        override fun memberInfoChanged(event: MemberInfoChange<KtNamedDeclaration, KotlinMemberInfo>) {
            super.memberInfoChanged(event)
            val superClass = superClass ?: return
            if (superClass != lastSuperClass) {
                lastSuperClass = superClass
                val isInterface = superClass is KtClass && superClass.isInterface()
                event.changedMembers.forEach { it.isToAbstract = isInterface }
                setSuperClass(superClass)
            }
        }
    }

    private val memberInfoStorage: KotlinMemberInfoStorage get() = myMemberInfoStorage

    private val sourceClass: KtClassOrObject get() = myClass as KtClassOrObject

    override fun getDimensionServiceKey() = "#" + this::class.java.name

    override fun getSuperClass() = super.getSuperClass()

    override fun createMemberInfoModel(): MemberInfoModel<KtNamedDeclaration, KotlinMemberInfo> =
        MemberInfoModelImpl(sourceClass, preselection, getInterfaceContainmentVerifier { selectedMemberInfos })

  override fun initClassCombo(classCombo: JComboBox<*>) {
        @Suppress("UNCHECKED_CAST") 
        val castedClassCombo = classCombo as JComboBox<PsiNamedElement>
        
        castedClassCombo.setRenderer(KotlinOrJavaClassCellRenderer())
        castedClassCombo.addItemListener(fun(e: ItemEvent) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                if (myMemberSelectionPanel == null) return
                val table = myMemberSelectionPanel.getTable()
                if (table == null) return
                table.setMemberInfos(myMemberInfos)
                table.fireExternalDataChange()
            }
        })
    }

    override fun getPreselection() = mySuperClasses.firstOrNull { !it.isInterfaceClass() } ?: mySuperClasses.firstOrNull()

    override fun createMemberSelectionTable(infos: MutableList<KotlinMemberInfo>) =
      KotlinMemberSelectionTable(infos, null, RefactoringBundle.message("make.abstract"))

    override fun isOKActionEnabled() = selectedMemberInfos.size > 0

    override fun doAction() {
        val selectedMembers = selectedMemberInfos
        val targetClass = superClass!!
        checkPullUpConflicts(project, sourceClass, targetClass, selectedMembers, { close(OK_EXIT_CODE) }) {
            invokeRefactoring(createProcessor(sourceClass, targetClass, selectedMembers))
        }
    }

    companion object {
        fun createProcessor(
          sourceClass: KtClassOrObject,
          targetClass: PsiNamedElement,
          memberInfos: List<KotlinMemberInfo>
        ): PullUpProcessor {
            val targetPsiClass = targetClass as? PsiClass ?: (targetClass as KtClass).toLightClass()
            return PullUpProcessor(
                sourceClass.toLightClass() ?: error("can't build lightClass for $sourceClass"),
                targetPsiClass,
                memberInfos.mapNotNull { it.toJavaMemberInfo() }.toTypedArray(),
                DocCommentPolicy(KotlinCommonRefactoringSettings.Companion.getInstance().PULL_UP_MEMBERS_JAVADOC)
            )
        }
    }
}