// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google

import org.intellij.plugins.markdown.MarkdownBundle

class GoogleAuthorizedUserException : RuntimeException(MarkdownBundle.message("markdown.google.accounts.user.unauthenticated.error"))
