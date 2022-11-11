// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import org.jetbrains.kotlin.tools.projectWizard.core.RandomIdGenerator
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs


sealed class Identificator {
    abstract val id: String

    final override fun equals(other: Any?): Boolean = other.safeAs<Identificator>()?.id == id
    final override fun hashCode(): Int = id.hashCode()
    final override fun toString(): String = id
}

class GeneratedIdentificator(prefix: String?) : Identificator() {
    override val id = "${prefix.orEmpty()}_${RandomIdGenerator.generate()}"
}


interface IdentificatorOwner {
    val identificator: Identificator
}

val IdentificatorOwner.id
    get() = identificator.id
