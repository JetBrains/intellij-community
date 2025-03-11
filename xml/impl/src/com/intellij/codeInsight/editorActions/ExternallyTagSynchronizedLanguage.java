// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.ApiStatus;

/**
 * A marker interface for the languages that have an external tag synchronizer attached and that don't need the tag synchronization from the
 * {@link XmlTagNameSynchronizer}
 */
@ApiStatus.Internal
public interface ExternallyTagSynchronizedLanguage {
}
