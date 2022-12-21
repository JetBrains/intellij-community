// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.ide.warmup.WarmupConfigurationOfCLIConfigurator

class MavenWarmupConfiguration : WarmupConfigurationOfCLIConfigurator(MavenCommandLineInspectionProjectConfigurator())