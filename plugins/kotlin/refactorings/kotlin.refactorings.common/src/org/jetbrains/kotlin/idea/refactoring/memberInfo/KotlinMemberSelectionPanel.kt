// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.awt.BorderLayout

class KotlinMemberSelectionPanel(
    @NlsContexts.DialogTitle title: String? = null,
    memberInfo: List<KotlinMemberInfo>,
    @Nls abstractColumnHeader: String? = null
) : AbstractMemberSelectionPanel<KtNamedDeclaration, KotlinMemberInfo>() {
    private val table = createMemberSelectionTable(memberInfo, abstractColumnHeader)

    init {
        layout = BorderLayout()
        val scrollPane = ScrollPaneFactory.createScrollPane(table)
        title?.let { add(SeparatorFactory.createSeparator(title, table), BorderLayout.NORTH) }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createMemberSelectionTable(
        memberInfo: List<KotlinMemberInfo>,
        @Nls abstractColumnHeader: String?
    ): KotlinMemberSelectionTable {
        return KotlinMemberSelectionTable(memberInfo, null, abstractColumnHeader)
    }

    override fun getTable() = table
}