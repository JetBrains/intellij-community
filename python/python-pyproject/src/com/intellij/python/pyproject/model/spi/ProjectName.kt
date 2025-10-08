package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.util.NlsSafe

/**
 * project name in pyproject.toml
 */
@JvmInline
value class ProjectName(val name: @NlsSafe String)