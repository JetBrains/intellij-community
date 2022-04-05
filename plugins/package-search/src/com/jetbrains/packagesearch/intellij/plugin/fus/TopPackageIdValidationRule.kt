package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomValidationRule

internal class TopPackageIdValidationRule : LocalFileCustomValidationRule(
    "top_package_id",
    PackageSearchEventsLogger::class.java,
    "/fus/allowed-packages"
)
