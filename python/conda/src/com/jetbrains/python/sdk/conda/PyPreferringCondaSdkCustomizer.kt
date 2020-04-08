package com.jetbrains.python.sdk.conda

class PyPreferringCondaSdkCustomizer : PyCondaSdkCustomizer {
  override val preferCondaEnvironments: Boolean
    get() = true
}