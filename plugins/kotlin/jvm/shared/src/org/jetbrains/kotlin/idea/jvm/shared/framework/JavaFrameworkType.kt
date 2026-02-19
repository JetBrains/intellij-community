// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.framework

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import javax.swing.Icon

class JavaFrameworkType : FrameworkTypeEx("kotlin-java-framework-id") {

    override fun createProvider(): FrameworkSupportInModuleProvider = JavaFrameworkSupportProvider()

    override fun getPresentableName() = KotlinJvmBundle.message("presentable.name.kotlin.jvm")

    override fun getIcon(): Icon = KotlinIcons.SMALL_LOGO

    companion object {
        val instance: JavaFrameworkType
            get() = EP_NAME.findExtension(JavaFrameworkType::class.java)
                ?: error("can't find extension 'JavaFrameworkType'")
    }
}
