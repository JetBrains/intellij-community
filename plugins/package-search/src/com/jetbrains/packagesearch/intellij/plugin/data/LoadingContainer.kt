package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class LoadingContainer(private val project: Project) {

    enum class LoadingState {
        LOADING, IDLE
    }

    private val loadingStates: Channel<MutableStateFlow<LoadingState>> = Channel()

    fun addLoadingState(): MutableStateFlow<LoadingState> {
        val newFlow = MutableStateFlow(LoadingState.IDLE)
        project.lifecycleScope.launch { loadingStates.send(newFlow) }
        return newFlow
    }

    val loadingFlow = channelFlow {
      val allFlows = mutableListOf<MutableStateFlow<LoadingState>>()
      loadingStates.consumeAsFlow()
        .collect {
          allFlows.add(it)
          send(combine(allFlows) {
            if (it.any { it == LoadingState.LOADING }) LoadingState.LOADING else LoadingState.IDLE
          })
        }
    }.flatMapLatest { it }
        .stateIn(project.lifecycleScope, SharingStarted.Lazily, LoadingState.IDLE)
}