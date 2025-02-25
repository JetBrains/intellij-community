
import sys
import traceback


"""
A utility class for managing and mapping 
IPython cells to their corresponding `kotlin_cell_id`, 
enabling breakpoint identification during debugging Jupyter notebooks.

When debugging is started, we send pairs of 
`<kotlin_cell_id, line_number_with_a_breakpoint>` from the IDE side.
`kotlin_cell_id` is computed as a sha256(cell_content).

Each IPython cell, when executed, generates a `.py` file. 
This class maintains a dictionary 
to map these `.py` files to their respective `kotlin_cell_id`, 
allowing finding existed breakpoints. 

Attributes:
    cell_filename_to_cell_id_map (dict): A mapping of 
        IPython-generated `.py` file names to their respective `kotlin_cell_id`.

Methods:
    cache_cell_mapping(cell_filename): Adds a new 
        IPython-generated cell `.py` file and its corresponding `kotlin_cell_id` to the cache. 
"""
class JupyterDebugCellInfo(object):
    cell_filename_to_cell_id_map = {}

    def cache_cell_mapping(self, cell_filename):
        try:
            # Skip caching for non-cell files like libraries or unrelated files
            if not self.__is_cell_filename(cell_filename):
                return

            cell_content = self.__get_cell_content(cell_filename)
            self.cell_filename_to_cell_id_map[cell_filename] = self.__compute_cell_content_hash(cell_content)
        except Exception as _:
            pass

    def __is_cell_filename(self, filename):
        import linecache
        return filename in linecache.cache

    def __get_cell_content(self, cell_filename):
        import linecache
        return "".join(linecache.cache[cell_filename][2])

    def __compute_cell_content_hash(self, cell_content):
        import hashlib
        return hashlib.sha256(cell_content.encode('utf-8')).hexdigest()


def remove_imported_pydev_package():
    """
    Some third-party libraries might contain sources of PyDev and its modules' names will shadow PyCharm's
    helpers modules. If `pydevd` was imported from site-packages, we should remove it and all its submodules and
    re-import again (with proper sys.path)
    """
    pydev_module = sys.modules.get('pydevd', None)
    if pydev_module is not None and 'site-packages' in str(pydev_module):
        import os
        pydev_dir = os.listdir(os.path.dirname(pydev_module.__file__))
        pydev_dir.append('pydevd')
        imported_modules = set(sys.modules.keys())
        for imported_module in imported_modules:
            for dir in pydev_dir:
                if imported_module.startswith(dir):
                    sys.modules.pop(imported_module, None)


def attach_to_debugger(debugger_port):
    ipython_shell = get_ipython()

    import pydevd
    from _pydev_bundle import pydev_localhost

    debugger = pydevd.PyDB()
    debugger.frame_eval_func = None
    ipython_shell.debugger = debugger
    try:
        debugger.connect(pydev_localhost.get_localhost(), debugger_port)
        debugger.prepare_to_run(enable_tracing_from_start=False)
    except:
        traceback.print_exc()
        sys.stderr.write('Failed to connect to target debugger.\n')

    # should be executed only once for kernel
    if not hasattr(ipython_shell, "pydev_cell_info"):
        ipython_shell.pydev_cell_info = JupyterDebugCellInfo()
    # save link in debugger for quick access
    debugger.cell_info = ipython_shell.pydev_cell_info
    debugger.warn_once_map = {}


def enable_tracing():
    debugger = get_ipython().debugger
    # SetTrace should be enough, because Jupyter creates new frame every time
    debugger.enable_tracing()
    # debugger.enable_tracing_in_frames_while_running()


def disable_tracing():
    ipython_shell = get_ipython()
    if hasattr(ipython_shell, "debugger"):
        ipython_shell.debugger.disable_tracing()
        kill_pydev_threads(ipython_shell.debugger)


def kill_pydev_threads(py_db):
    from _pydevd_bundle.pydevd_kill_all_pydevd_threads import kill_all_pydev_threads
    py_db.finish_debugging_session()
    kill_all_pydev_threads()
