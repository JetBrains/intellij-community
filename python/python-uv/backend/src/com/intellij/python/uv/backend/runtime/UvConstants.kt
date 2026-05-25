// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.runtime

/**
 * Environment variables recognised by `uv` and `uvx`. See https://docs.astral.sh/uv/reference/environment/.
 */
@Suppress("unused")
object UvConstants {
  object AppEnvVars {
    const val PYTHON: String = "UV_PYTHON"
    const val MANAGED_PYTHON: String = "UV_MANAGED_PYTHON"
    const val NO_MANAGED_PYTHON: String = "UV_NO_MANAGED_PYTHON"
    const val PYTHON_DOWNLOADS: String = "UV_PYTHON_DOWNLOADS"
    const val PYTHON_INSTALL_DIR: String = "UV_PYTHON_INSTALL_DIR"
    const val OFFLINE: String = "UV_OFFLINE"
    const val NO_PROGRESS: String = "UV_NO_PROGRESS"
    const val NATIVE_TLS: String = "UV_NATIVE_TLS"
    const val INSECURE_HOST: String = "UV_INSECURE_HOST"
    const val NO_COLOR: String = "NO_COLOR" // https://no-color.org
    const val FORCE_COLOR: String = "FORCE_COLOR"
  }

  object ConfigEnvVars {
    const val PROJECT: String = "UV_PROJECT"
    const val WORKING_DIR: String = "UV_WORKING_DIR"
    const val CONFIG_FILE: String = "UV_CONFIG_FILE"
    const val NO_CONFIG: String = "UV_NO_CONFIG"
    const val CACHE_DIR: String = "UV_CACHE_DIR"
    const val NO_CACHE: String = "UV_NO_CACHE"
    const val TOOL_DIR: String = "UV_TOOL_DIR"
    const val TOOL_BIN_DIR: String = "UV_TOOL_BIN_DIR"
  }

  object PipEnvVars {
    const val INDEX: String = "UV_INDEX"
    const val DEFAULT_INDEX: String = "UV_DEFAULT_INDEX"
    const val INDEX_URL: String = "UV_INDEX_URL"
    const val EXTRA_INDEX_URL: String = "UV_EXTRA_INDEX_URL"
    const val FIND_LINKS: String = "UV_FIND_LINKS"
    const val INDEX_STRATEGY: String = "UV_INDEX_STRATEGY"
    const val KEYRING_PROVIDER: String = "UV_KEYRING_PROVIDER"
    const val RESOLUTION: String = "UV_RESOLUTION"
    const val PRERELEASE: String = "UV_PRERELEASE"
    const val FORK_STRATEGY: String = "UV_FORK_STRATEGY"
    const val EXCLUDE_NEWER: String = "UV_EXCLUDE_NEWER"
    const val CONSTRAINT: String = "UV_CONSTRAINT"
    const val BUILD_CONSTRAINT: String = "UV_BUILD_CONSTRAINT"
    const val OVERRIDE: String = "UV_OVERRIDE"
  }

  object UvxEnvVars {
    const val ISOLATED: String = "UV_ISOLATED"
    const val ENV_FILE: String = "UV_ENV_FILE"
    const val NO_ENV_FILE: String = "UV_NO_ENV_FILE"
    const val TORCH_BACKEND: String = "UV_TORCH_BACKEND"
  }
}
