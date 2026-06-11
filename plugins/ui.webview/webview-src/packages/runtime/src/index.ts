// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/// <reference path="../../impl/src/assets.d.ts" />

import { installWebViewBridge } from "../../impl/src/bridge"
import { installWebViewPlatformFeatures } from "../../impl/src/platformFeatures"

installWebViewPlatformFeatures(installWebViewBridge())
