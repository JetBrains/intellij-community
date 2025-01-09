package com.intellij.debugger.streams.test

import com.intellij.debugger.streams.wrapper.StreamChain

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