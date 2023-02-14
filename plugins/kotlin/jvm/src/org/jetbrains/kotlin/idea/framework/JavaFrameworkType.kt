// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinJvmBundle
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
