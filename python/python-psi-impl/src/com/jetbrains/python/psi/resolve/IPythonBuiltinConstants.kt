package com.jetbrains.python.psi.resolve

object IPythonBuiltinConstants {
  const val DISPLAY = "display"
  const val GET_IPYTHON = "get_ipython"
  const val IN = "In"
  const val OUT = "Out"

  const val OUT_HIST_DICT = "output_hist"
  const val IN_HIST_DICT = "input_hist_parsed"

  const val DISPLAY_DOTTED_PATH_OLD = "IPython.core.display.display"
  const val DISPLAY_DOTTED_PATH_NEW = "IPython.core.display_functions.display"
  const val GET_IPYTHON_DOTTED_PATH = "IPython.core.getipython.get_ipython"
  const val HISTORY_MANAGER_DOTTED_PATH = "IPython.core.history.HistoryManager"

  const val DOUBLE_UNDERSCORE = "__"
  const val TRIPLE_UNDERSCORE = "___"

  // MAGICS
  private const val AUTOCALL_MAGIC = "autocall"
  private const val AUTOMAGIC_MAGIC = "automagic"
  val AUTOMAGIC_MAGICS = listOf(AUTOCALL_MAGIC, AUTOMAGIC_MAGIC)

  private const val ALIAS_MAGIC_MAGIC = "alias_magic"
  private const val COLORS_MAGIC = "colors"
  private const val DOCTEST_MODE_MAGIC = "doctest_mode"
  private const val GUI_MAGIC = "gui"
  private const val LSMAGIC_MAGIC = "lsmagic"
  private const val MAGIC_MAGIC = "magic"
  private const val NOTEBOOK_MAGIC = "notebook"
  private const val PAGE_MAGIC = "page"
  private const val PPRINT_MAGIC = "pprint"
  private const val PRECISION_MAGIC = "precision"
  private const val QUICKREF_MAGIC = "quickref"
  private const val XMODE_MAGIC = "xmode"
  val BASIC_MAGICS = listOf(ALIAS_MAGIC_MAGIC, COLORS_MAGIC, DOCTEST_MODE_MAGIC, GUI_MAGIC,
                            LSMAGIC_MAGIC, MAGIC_MAGIC, NOTEBOOK_MAGIC, PAGE_MAGIC,
                            PPRINT_MAGIC, PRECISION_MAGIC, QUICKREF_MAGIC, XMODE_MAGIC)


  private const val EDIT_MAGIC = "edit"
  private const val LOADPY_MAGIC = "loadpy"
  private const val LOAD_MAGIC = "load"
  private const val PASTEBIN_MAGIC = "pastebin"
  private const val SAVE_MAGIC = "save"
  val CODE_MAGICS = listOf(EDIT_MAGIC, LOADPY_MAGIC, LOAD_MAGIC, PASTEBIN_MAGIC, SAVE_MAGIC)

  private const val CONFIG_MAGIC = "config"
  val CONFIG_MAGICS = listOf(CONFIG_MAGIC)

  private const val DEBUG_MAGIC = "debug"
  private const val MACRO_MAGIC = "macro"
  private const val PDB_MAGIC = "pdb"
  private const val PRUN_MAGIC = "prun"
  private const val RUN_MAGIC = "run"
  private const val TB_MAGIC = "tb"
  private const val TIMEIT_MAGIC = "timeit"
  private const val TIME_MAGIC = "time"
  val EXECUTION_MAGICS = listOf(DEBUG_MAGIC, MACRO_MAGIC, PDB_MAGIC, PRUN_MAGIC,
                                RUN_MAGIC, TB_MAGIC, TIMEIT_MAGIC, TIME_MAGIC)

  private const val LOAD_EXT_MAGIC = "load_ext"
  private const val RELOAD_EXT_MAGIC = "reload_ext"
  private const val UNLOAD_EXT_MAGIC = "unload_ext"
  val EXTENSION_MAGICS = listOf(LOAD_EXT_MAGIC, RELOAD_EXT_MAGIC, UNLOAD_EXT_MAGIC)

  private const val HISTORY_MAGIC = "history"
  private const val RECALL_MAGIC = "recall"
  private const val RERUN_MAGIC = "rerun"
  val HISTORY_MAGICS = listOf(HISTORY_MAGIC, RECALL_MAGIC, RERUN_MAGIC)

  private const val LOGOFF_MAGIC = "logoff"
  private const val LOGON_MAGIC = "logon"
  private const val LOGSTART_MAGIC = "logstart"
  private const val LOGSTATE_MAGIC = "logstate"
  private const val LOGSTOP_MAGIC = "logstop"
  val LOGGING_MAGICS = listOf(LOGOFF_MAGIC, LOGON_MAGIC, LOGSTART_MAGIC, LOGSTATE_MAGIC, LOGSTOP_MAGIC)

  private const val PDEF_MAGIC = "pdef"
  private const val PDOC_MAGIC = "pdoc"
  private const val PFILE_MAGIC = "pfile"
  private const val PINFO2_MAGIC = "pinfo2"
  private const val PINFO_MAGIC = "pinfo"
  private const val PSEARCH_MAGIC = "psearch"
  private const val PSOURCE_MAGIC = "psource"
  private const val RESET_MAGIC = "reset"
  private const val RESET_SELECTIVE_MAGIC = "reset_selective"
  private const val WHOS_MAGIC = "whos"
  private const val WHO_LS_MAGIC = "who_ls"
  private const val WHO_MAGIC = "who"
  private const val XDEL_MAGIC = "xdel"
  val NAMESPACE_MAGICS = listOf(PDEF_MAGIC, PDOC_MAGIC, PFILE_MAGIC, PINFO2_MAGIC,
                                PINFO_MAGIC, PSEARCH_MAGIC, PSOURCE_MAGIC, RESET_MAGIC,
                                RESET_SELECTIVE_MAGIC, WHOS_MAGIC, WHO_LS_MAGIC, WHO_MAGIC,
                                XDEL_MAGIC)

  private const val ALIAS_MAGIC = "alias"
  private const val BOOKMARK_MAGIC = "bookmark"
  private const val CD_MAGIC = "cd"
  private const val DHIST_MAGIC = "dhist"
  private const val DIRS_MAGIC = "dirs"
  private const val ENV_MAGIC = "env"
  private const val POPD_MAGIC = "popd"
  private const val PUSHD_MAGIC = "pushd"
  private const val PWD_MAGIC = "pwd"
  private const val PYCAT_MAGIC = "pycat"
  private const val REGASHX_MAGIC = "rehashx"
  private const val SC_MAGIC = "sc"
  private const val SET_ENV_MAGIC = "set_env"
  private const val SX_MAGIC = "sx"
  private const val UNALIAS_MAGIC = "unalias"
  val OS_MAGICS = listOf(ALIAS_MAGIC, BOOKMARK_MAGIC, CD_MAGIC, DHIST_MAGIC,
                         DIRS_MAGIC, ENV_MAGIC, POPD_MAGIC, PUSHD_MAGIC,
                         PWD_MAGIC, PYCAT_MAGIC, REGASHX_MAGIC, SC_MAGIC,
                         SET_ENV_MAGIC, SX_MAGIC, UNALIAS_MAGIC)

  private const val CONDA_MAGIC = "conda"
  private const val PIP_MAGIC = "pip"
  val PACKAGING_MAGICS = listOf(CONDA_MAGIC, PIP_MAGIC)

  private const val KILLBGSCRIPTS_MAGIC = "killbgscripts"
  private const val MATPLOTLIB_MAGIC = "matplotlib"
  private const val PYLAB_MAGIC = "pylab"
  val PYLAB_MAGICS = listOf(KILLBGSCRIPTS_MAGIC, MATPLOTLIB_MAGIC, PYLAB_MAGIC)

  private const val AUTOAWAITS_MAGIC = "autoawait"
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