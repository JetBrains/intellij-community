// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase
import org.jetbrains.kotlin.incremental.components.LookupTracker

private val TESTING_CONTEXT = JpsElementChildRoleBase.create<JpsSimpleElement<out TestingContext>>("Testing kcontext")

@TestOnly
fun JpsProject.setTestingContext(context: TestingContext) {
    val dataContainer = JpsElementFactory.getInstance().createSimpleElement(context)
    container.setChild(TESTING_CONTEXT, dataContainer)
}

val JpsProject.testingContext: TestingContext?
    get() = container.getChild(TESTING_CONTEXT)?.data

val CompileContext.testingContext: TestingContext?
    get() = projectDescriptor?.project?.testingContext

class TestingContext(
    val lookupTracker: LookupTracker,
    val buildLogger: TestingBuildLogger?
) {
    var kotlinCompileContext: KotlinCompileContext? = null
}
