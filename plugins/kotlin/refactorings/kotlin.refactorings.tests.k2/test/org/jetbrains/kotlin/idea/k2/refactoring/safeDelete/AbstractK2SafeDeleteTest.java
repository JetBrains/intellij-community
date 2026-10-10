// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete;

import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.refactoring.safeDelete.AbstractSafeDeleteTest;

import java.util.List;

public abstract class AbstractK2SafeDeleteTest extends AbstractSafeDeleteTest {
    @Override
    protected @NotNull List<ModPlanOption<?>> knownOptions() {
        return List.of(
                ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS,
                ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE,
                ModPlanSafeDeleteOptions.DELETE_HIERARCHY,
                ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL,
                ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS
        );
    }
}
