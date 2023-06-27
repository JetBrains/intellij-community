package com.intellij.turboComplete.analysis

import com.intellij.turboComplete.DelegatingKindExecutionListener
import com.intellij.turboComplete.ranking.DelegatingKindRankingListener

interface DelegatingPipelineListener
  : PipelineListener,
    DelegatingKindRankingListener<PipelineListener>,
    DelegatingKindExecutionListener<PipelineListener>