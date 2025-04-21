package com.intellij.python.hatch.runtime

@Suppress("unused")
object HatchConstants {
  object AppEnvVars {
    const val ENV: String = "HATCH_ENV"
    const val ENV_ACTIVE: String = "HATCH_ENV_ACTIVE"
    const val ENV_OPTION_PREFIX: String = "HATCH_ENV_TYPE_"
    const val QUIET: String = "HATCH_QUIET"
    const val VERBOSE: String = "HATCH_VERBOSE"
    const val INTERACTIVE: String = "HATCH_INTERACTIVE"
    const val PYTHON: String = "HATCH_PYTHON"
    const val NO_COLOR: String = "NO_COLOR" // https://no-color.org
    const val FORCE_COLOR: String = "FORCE_COLOR"
  }

  object ConfigEnvVars {
    const val PROJECT: String = "HATCH_PROJECT"
    const val DATA: String = "HATCH_DATA_DIR"
    const val CACHE: String = "HATCH_CACHE_DIR"
    const val CONFIG: String = "HATCH_CONFIG"
  }

  object PublishEnvVars {
    const val USER: String = "HATCH_INDEX_USER"
    const val AUTH: String = "HATCH_INDEX_AUTH"
    const val REPO: String = "HATCH_INDEX_REPO"
    const val CA_CERT: String = "HATCH_INDEX_CA_CERT"
    const val CLIENT_CERT: String = "HATCH_INDEX_CLIENT_CERT"
    const val CLIENT_KEY: String = "HATCH_INDEX_CLIENT_KEY"
    const val PUBLISHER: String = "HATCH_PUBLISHER"
    const val OPTIONS: String = "HATCH_PUBLISHER_OPTIONS"
  }

  object PythonEnvVars {
    const val CUSTOM_SOURCE_PREFIX: String = "HATCH_PYTHON_CUSTOM_SOURCE_"
    const val CUSTOM_PATH_PREFIX: String = "HATCH_PYTHON_CUSTOM_PATH_"
    const val CUSTOM_VERSION_PREFIX: String = "HATCH_PYTHON_CUSTOM_VERSION_"
  }

  object VersionEnvVars {
    const val VALIDATE_BUMP: String = "HATCH_VERSION_VALIDATE_BUMP"
  }
}