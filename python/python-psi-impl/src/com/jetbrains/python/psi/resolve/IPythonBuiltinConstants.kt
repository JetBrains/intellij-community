package com.jetbrains.python.psi.resolve

object IPythonBuiltinConstants {
  const val DISPLAY = "display"
  const val GET_IPYTHON = "get_ipython"
  const val IN = "In"
  const val OUT = "Out"

  const val IPYTHON_PACKAGE = "IPython"
  const val CORE_PACKAGE = "core"

  const val HISTORY_MANAGER = "HistoryManager"
  const val OUT_HIST_DICT = "output_hist"
  const val IN_HIST_DICT = "input_hist_parsed"

  const val DISPLAY_DOTTED_PATH = "IPython.core.display.display"
  const val GET_IPYTHON_DOTTED_PATH = "IPython.core.getipython.get_ipython"
  const val HISTORY_MANAGER_DOTTED_PATH = "IPython.core.history.HistoryManager"

  const val DOUBLE_UNDERSCORE = "__"
  const val TRIPLE_UNDERSCORE = "___"
}