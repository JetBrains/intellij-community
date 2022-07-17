// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.elastic

data class ElasticCredentials(
    val host: String,
    val username: String,
    val password: String,
    val index: String,
)

fun credentialsByEnvVariables(index: String): ElasticCredentials =
    ElasticCredentials(
        host = System.getenv("ES_HOSTNAME"),
        username = System.getenv("ES_USERNAME"),
        password = System.getenv("ES_PASSWORD"),
        index = index
    )
