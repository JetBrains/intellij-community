// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtElement
import java.awt.BorderLayout

class KotlinMemberSelectionPanel<I : KotlinMemberInfo> @JvmOverloads constructor(
    @NlsContexts.DialogTitle title: String? = null,
    memberInfo: List<I>,
    @Nls abstractColumnHeader: String? = null,
    memberInfoModel: MemberInfoModel<KtElement, I>? = null,
) : AbstractMemberSelectionPanel<KtElement, I>() {
    private val table: KotlinMemberSelectionTable<I> = createMemberSelectionTable(memberInfo, memberInfoModel, abstractColumnHeader)

    init {
        layout = BorderLayout()
        val scrollPane = ScrollPaneFactory.createScrollPane(table)
        title?.let { add(SeparatorFactory.createSeparator(title, table), BorderLayout.NORTH) }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createMemberSelectionTable(
        memberInfo: List<I>,
        memberInfoModel: MemberInfoModel<KtElement, I>?,
        @Nls abstractColumnHeader: String?
    ): KotlinMemberSelectionTable<I> {
        return KotlinMemberSelectionTable(memberInfo, memberInfoModel, abstractColumnHeader)
    }

    override fun getTable(): KotlinMemberSelectionTable<I> = table
}
