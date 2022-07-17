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

  const val DISPLAY_DOTTED_PATH_OLD = "IPython.core.display.display"
  const val DISPLAY_DOTTED_PATH_NEW = "IPython.core.display_functions.display"
  const val GET_IPYTHON_DOTTED_PATH = "IPython.core.getipython.get_ipython"
  const val HISTORY_MANAGER_DOTTED_PATH = "IPython.core.history.HistoryManager"

  const val DOUBLE_UNDERSCORE = "__"
  const val TRIPLE_UNDERSCORE = "___"

  // MAGICS
  const val AUTOCALL_MAGIC = "autocall"
  const val AUTOMAGIC_MAGIC = "automagic"
  val AUTOMAGIC_MAGICS = listOf(AUTOCALL_MAGIC, AUTOMAGIC_MAGIC)

  const val ALIAS_MAGIC_MAGIC = "alias_magic"
  const val COLORS_MAGIC = "colors"
  const val DOCTEST_MODE_MAGIC = "doctest_mode"
  const val GUI_MAGIC = "gui"
  const val LSMAGIC_MAGIC = "lsmagic"
  const val MAGIC_MAGIC = "magic"
  const val NOTEBOOK_MAGIC = "notebook"
  const val PAGE_MAGIC = "page"
  const val PPRINT_MAGIC = "pprint"
  const val PRECISION_MAGIC = "precision"
  const val QUICKREF_MAGIC = "quickref"
  const val XMODE_MAGIC = "xmode"
  val BASIC_MAGICS = listOf(ALIAS_MAGIC_MAGIC, COLORS_MAGIC, DOCTEST_MODE_MAGIC, GUI_MAGIC,
                            LSMAGIC_MAGIC, MAGIC_MAGIC, NOTEBOOK_MAGIC, PAGE_MAGIC,
                            PPRINT_MAGIC, PRECISION_MAGIC, QUICKREF_MAGIC, XMODE_MAGIC)


  const val EDIT_MAGIC = "edit"
  const val LOADPY_MAGIC = "loadpy"
  const val LOAD_MAGIC = "load"
  const val PASTEBIN_MAGIC = "pastebin"
  const val SAVE_MAGIC = "save"
  val CODE_MAGICS = listOf(EDIT_MAGIC, LOADPY_MAGIC, LOAD_MAGIC, PASTEBIN_MAGIC, SAVE_MAGIC)

  const val CONFIG_MAGIC = "config"
  val CONFIG_MAGICS = listOf(CONFIG_MAGIC)

  const val DEBUG_MAGIC = "debug"
  const val MACRO_MAGIC = "macro"
  const val PDB_MAGIC = "pdb"
  const val PRUN_MAGIC = "prun"
  const val RUN_MAGIC = "run"
  const val TB_MAGIC = "tb"
  const val TIMEIT_MAGIC = "timeit"
  const val TIME_MAGIC = "time"
  val EXECUTION_MAGICS = listOf(DEBUG_MAGIC, MACRO_MAGIC, PDB_MAGIC, PRUN_MAGIC,
                                RUN_MAGIC, TB_MAGIC, TIMEIT_MAGIC, TIME_MAGIC)

  const val LOAD_EXT_MAGIC = "load_ext"
  const val RELOAD_EXT_MAGIC = "reload_ext"
  const val UNLOAD_EXT_MAGIC = "unload_ext"
  val EXTENSION_MAGICS = listOf(LOAD_EXT_MAGIC, RELOAD_EXT_MAGIC, UNLOAD_EXT_MAGIC)

  const val HISTORY_MAGIC = "history"
  const val RECALL_MAGIC = "recall"
  const val RERUN_MAGIC = "rerun"
  val HISTORY_MAGICS = listOf(HISTORY_MAGIC, RECALL_MAGIC, RERUN_MAGIC)

  const val LOGOFF_MAGIC = "logoff"
  const val LOGON_MAGIC = "logon"
  const val LOGSTART_MAGIC = "logstart"
  const val LOGSTATE_MAGIC = "logstate"
  const val LOGSTOP_MAGIC = "logstop"
  val LOGGING_MAGICS = listOf(LOGOFF_MAGIC, LOGON_MAGIC, LOGSTART_MAGIC, LOGSTATE_MAGIC, LOGSTOP_MAGIC)

  const val PDEF_MAGIC = "pdef"
  const val PDOC_MAGIC = "pdoc"
  const val PFILE_MAGIC = "pfile"
  const val PINFO2_MAGIC = "pinfo2"
  const val PINFO_MAGIC = "pinfo"
  const val PSEARCH_MAGIC = "psearch"
  const val PSOURCE_MAGIC = "psource"
  const val RESET_MAGIC = "reset"
  const val RESET_SELECTIVE_MAGIC = "reset_selective"
  const val WHOS_MAGIC = "whos"
  const val WHO_LS_MAGIC = "who_ls"
  const val WHO_MAGIC = "who"
  const val XDEL_MAGIC = "xdel"
  val NAMESPACE_MAGICS = listOf(PDEF_MAGIC, PDOC_MAGIC, PFILE_MAGIC, PINFO2_MAGIC,
                                PINFO_MAGIC, PSEARCH_MAGIC, PSOURCE_MAGIC, RESET_MAGIC,
                                RESET_SELECTIVE_MAGIC, WHOS_MAGIC, WHO_LS_MAGIC, WHO_MAGIC,
                                XDEL_MAGIC)

  const val ALIAS_MAGIC = "alias"
  const val BOOKMARK_MAGIC = "bookmark"
  const val CD_MAGIC = "cd"
  const val DHIST_MAGIC = "dhist"
  const val DIRS_MAGIC = "dirs"
  const val ENV_MAGIC = "env"
  const val POPD_MAGIC = "popd"
  const val PUSHD_MAGIC = "pushd"
  const val PWD_MAGIC = "pwd"
  const val PYCAT_MAGIC = "pycat"
  const val REGASHX_MAGIC = "rehashx"
  const val SC_MAGIC = "sc"
  const val SET_ENV_MAGIC = "set_env"
  const val SX_MAGIC = "sx"
  const val UNALIAS_MAGIC = "unalias"
  val OS_MAGICS = listOf(ALIAS_MAGIC, BOOKMARK_MAGIC, CD_MAGIC, DHIST_MAGIC,
                         DIRS_MAGIC, ENV_MAGIC, POPD_MAGIC, PUSHD_MAGIC,
                         PWD_MAGIC, PYCAT_MAGIC, REGASHX_MAGIC, SC_MAGIC,
                         SET_ENV_MAGIC, SX_MAGIC, UNALIAS_MAGIC)

  const val CONDA_MAGIC = "conda"
  const val PIP_MAGIC = "pip"
  val PACKAGING_MAGICS = listOf(CONDA_MAGIC, PIP_MAGIC)

  const val KILLBGSCRIPTS_MAGIC = "killbgscripts"
  const val MATPLOTLIB_MAGIC = "matplotlib"
  const val PYLAB_MAGIC = "pylab"
  val PYLAB_MAGICS = listOf(KILLBGSCRIPTS_MAGIC, MATPLOTLIB_MAGIC, PYLAB_MAGIC)

  const val AUTOAWAITS_MAGIC = "autoawait"
  val ASYNC_MAGICS = listOf(AUTOAWAITS_MAGIC)

  val MAGICS_LIST = listOf(AUTOMAGIC_MAGICS, BASIC_MAGICS, CODE_MAGICS, CONFIG_MAGICS,
                           EXECUTION_MAGICS, EXTENSION_MAGICS, HISTORY_MAGICS, LOGGING_MAGICS,
                           NAMESPACE_MAGICS, OS_MAGICS, PACKAGING_MAGICS, PYLAB_MAGICS,
                           ASYNC_MAGICS).flatten()

  const val MAGIC_AUTOMAGIC_DOTTED_PATH = "IPython.core.magics.auto.AutoMagics"
  const val MAGIC_BASIC_DOTTED_PATH = "IPython.core.magics.basic.BasicMagics"
  const val MAGIC_CODE_DOTTED_PATH = "IPython.core.magics.code.CodeMagics"
  const val MAGIC_CONFIG_DOTTED_PATH = "IPython.core.magics.config.ConfigMagics"
  const val MAGIC_EXECUTION_DOTTED_PATH = "IPython.core.magics.execution.ExecutionMagics"
  const val MAGIC_EXTENSION_DOTTED_PATH = "IPython.core.magics.extension.ExtensionMagics"
  const val MAGIC_HISTORY_DOTTED_PATH = "IPython.core.magics.history.HistoryMagics"
  const val MAGIC_LOGGING_DOTTED_PATH = "IPython.core.magics.logging.LoggingMagics"
  const val MAGIC_NAMESPACE_DOTTED_PATH = "IPython.core.magics.namespace.NamespaceMagics"
  const val MAGIC_OS_DOTTED_PATH = "IPython.core.magics.osm.OSMagics"
  const val MAGIC_PACKAGING_DOTTED_PATH = "IPython.core.magics.packaging.PackagingMagics"
  const val MAGIC_PYLAB_DOTTED_PATH = "IPython.core.magics.pylab.PylabMagics"
  const val MAGIC_ASYNC_DOTTED_PATH = "IPython.core.magics.basic.AsyncMagics"
}