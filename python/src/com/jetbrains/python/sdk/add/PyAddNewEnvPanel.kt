// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

/**
 * @author vlan
 */
abstract class PyAddNewEnvPanel : PyAddSdkPanel() {
  abstract val envName: String
}