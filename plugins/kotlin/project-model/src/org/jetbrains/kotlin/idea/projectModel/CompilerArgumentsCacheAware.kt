// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

/**
 * Entity is used for storing information about IDs for compiler argument caches.
 * API allows only to retrieve info, entity acts as immutable container
 *
 * @constructor Create empty Compiler arguments cache aware
 */
interface CompilerArgumentsCacheAware : Serializable, CacheOriginIdentifierAware {
    /**
     * Retrieve compiler argument value from cache
     *
     * @param cacheId Int cache id number to retrieve
     * @return `String` value of compiler argument or null if it is absent
     */
    fun getCached(cacheId: Int): String?


    /**
     * Distribute cache ids
     *
     * @return container with all used Cache Ids
     */
    fun distributeCacheIds(): Iterable<Int>
}