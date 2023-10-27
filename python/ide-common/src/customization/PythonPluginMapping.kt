// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.customization

import com.intellij.ide.customize.transferSettings.db.KnownBuiltInFeatures
import com.intellij.ide.customize.transferSettings.providers.vscode.mappings.VSCodePluginMappingBase

class PythonPluginMapping : VSCodePluginMappingBase(mapOf("ms-python.python" to KnownBuiltInFeatures.Python))
