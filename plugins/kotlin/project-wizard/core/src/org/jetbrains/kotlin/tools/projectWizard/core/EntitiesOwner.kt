// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core

import org.jetbrains.annotations.NonNls

interface EntitiesOwnerDescriptor {
    @get:NonNls
    val id: String
}

interface EntitiesOwner<D : EntitiesOwnerDescriptor> {
    val descriptor: D
}