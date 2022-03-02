// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.DescendentBasedRootFilter
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.util.isKotlinFileType

class KotlinSourceRootDetector : DescendentBasedRootFilter(OrderRootType.SOURCES, false, "sources", VirtualFile::isKotlinFileType)
