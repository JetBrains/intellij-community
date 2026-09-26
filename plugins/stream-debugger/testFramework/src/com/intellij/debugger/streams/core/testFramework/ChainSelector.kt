package com.intellij.debugger.streams.core.testFramework

import com.intellij.debugger.streams.core.wrapper.StreamChain

fun interface ChainSelector {
  fun select(chains: List<StreamChain>): StreamChain

  companion object {
    @JvmStatic
    fun byIndex(index: Int): ChainSelector {
      return ChainSelector { chains: List<StreamChain?> -> chains[index]!! }
    }

    @JvmStatic
    fun last(): ChainSelector {
      return ChainSelector { chains: List<StreamChain?> -> chains.last()!! }
    }
  }
}