// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineInfo

class CoroutineJobHierarchyProvider {
    private val jobsAndParents = mutableMapOf<String, String?>()

    fun findJobHierarchy(coroutineInfo: MirrorOfCoroutineInfo): List<String> {
        val jobs = mutableListOf<String>()
        var job = coroutineInfo.context?.job
        while (job != null) {
            val details = job.details
            jobs.add(details)
            if (jobsAndParents.contains(details)) {
                var parent = jobsAndParents[details]
                while (parent != null) {
                    jobs.add(parent)
                    parent = jobsAndParents[parent]
                }
                break
            }
            job = job.parent.getJob()
            jobsAndParents[details] = job?.details
        }
        return jobs
    }
}