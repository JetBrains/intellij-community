// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.core.extension.KotlinIndicesHelperExtension
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.synthetic.JavaSyntheticPropertiesScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner

class DebuggerFieldKotlinIndicesHelperExtension : KotlinIndicesHelperExtension {
    override fun appendExtensionCallables(
        consumer: MutableList<in CallableDescriptor>,
        moduleDescriptor: ModuleDescriptor,
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean,
        lookupLocation: LookupLocation,
    ) {
        val javaPropertiesScope = JavaSyntheticPropertiesScope(
            storageManager = LockBasedStorageManager.NO_LOCKS,
            lookupTracker = LookupTracker.DO_NOTHING,
            KotlinTypeRefiner.Default,
            supportJavaRecords = true,
        )

        val fieldScope = DebuggerFieldSyntheticScope(javaPropertiesScope)
        for (property in fieldScope.getSyntheticExtensionProperties(receiverTypes, lookupLocation)) {
            if (nameFilter(property.name.asString())) {
                consumer += property
            }
        }
    }
}
