package com.intellij.mermaid.lang.preview

import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

@DisabledIfEnvironmentVariable(named = "CI", matches = ".*")
@DisabledIfEnvironmentVariable(named = "AUTOMATED_PRODUCTION_BUILD", matches = ".*")
@DisabledIfEnvironmentVariable(named = "TEAMCITY_VERSION", matches = ".*")
annotation class DisabledInAutomation
