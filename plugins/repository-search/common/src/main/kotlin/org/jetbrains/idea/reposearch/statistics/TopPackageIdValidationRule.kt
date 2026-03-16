// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.reposearch.statistics

import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomValidationRule

/**
 * It is only allowed to collect information about the installed package using FUS iff its name is one of the top 1000 most popular packages.
 * This rule is used in [intellij.maven], [intellij.gradle.java.maven], [intellij.packagesearch] modules for FUS collectors.
 */
class TopPackageIdValidationRule : LocalFileCustomValidationRule(
  /* ruleId = */ "top_package_id",
  /* resource = */ TopPackageIdValidationRule::class.java,
  /* path = */ "/fus/allowed-packages"
)