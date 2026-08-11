// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { requireWebViewBridge } from "@jetbrains/intellij-webview"
import { installWebViewPlatformFeatures } from "./platformFeatures"

installWebViewPlatformFeatures(requireWebViewBridge())
