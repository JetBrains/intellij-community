// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.migration

import org.jetbrains.kotlin.idea.migration.MigrationInfo

/**
 * Marker interface for inspections that can be used during kotlin migrations
 */
interface MigrationFix {
    fun isApplicable(migrationInfo: MigrationInfo): Boolean
}
