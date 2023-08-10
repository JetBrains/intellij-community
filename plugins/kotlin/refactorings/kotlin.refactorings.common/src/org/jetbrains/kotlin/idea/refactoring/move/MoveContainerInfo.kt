// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import org.jetbrains.kotlin.name.FqName

sealed interface MoveContainerInfo {
    val fqName: FqName?

    object UnknownPackage : MoveContainerInfo {
        override val fqName: FqName? = null
    }

    class Package(override val fqName: FqName) : MoveContainerInfo {
        override fun equals(other: Any?) = other is Package && other.fqName == fqName

        override fun hashCode() = fqName.hashCode()
    }

    class Class(override val fqName: FqName) : MoveContainerInfo {
        override fun equals(other: Any?) = other is Class && other.fqName == fqName

        override fun hashCode() = fqName.hashCode()
    }
}

data class MoveContainerChangeInfo(val oldContainer: MoveContainerInfo, val newContainer: MoveContainerInfo)