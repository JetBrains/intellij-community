
import sys

backends = {'tk': 'tkagg',
            'gtk': 'gtkagg',
            'wx': 'wxagg',
            'qt': 'qt4agg', # qt3 not supported
            'qt4': 'qt4agg',
            'qt5': 'qt5agg',
            'qt6': 'qt6agg',
            'osx': 'macosx'}

# We also need a reverse backends2guis mapping that will properly choose which
# GUI support to activate based on the desired matplotlib backend.  For the
# most part it's just a reverse of the above dict, but we also need to add a
# few others that map to the same GUI manually:
backend2gui = dict(zip(backends.values(), backends.keys()))
backend2gui['qt4agg'] = 'qt4'
backend2gui['qt5agg'] = 'qt5'
backend2gui['qt6agg'] = 'qt6'
# In the reverse mapping, there are a few extra valid matplotlib backends that
# map to the same GUI support
backend2gui['gtk'] = backend2gui['gtkcairo'] = 'gtk'
backend2gui['wx'] = 'wx'
backend2gui['cocoaagg'] = 'osx'
backend2gui['qtagg'] = 'qt'

def do_enable_gui(guiname):
    from _pydev_bundle.pydev_versioncheck import versionok_for_gui
    if versionok_for_gui():
        try:
            from pydev_ipython.inputhook import enable_gui
            enable_gui(guiname)
        except:
            sys.stderr.write("Failed to enable GUI event loop integration for '%s'\n" % guiname)
            import traceback
            traceback.print_exc()
    elif guiname not in ['none', '', None]:
        # Only print a warning if the guiname was going to do something
        sys.stderr.write("Debug console: Python version does not support GUI event loop integration for '%s'\n" % guiname)
    # Return value does not matter, so return back what was sent
    return guiname


def find_gui_and_backend():
    """Return the gui and mpl backend."""
    matplotlib = sys.modules['matplotlib']
    # WARNING: this assumes matplotlib 1.1 or newer!!
    backend = matplotlib.rcParams['backend']
    if backend:
        backend = backend.lower()

    # In this case, we need to find what the appropriate gui selection call
    # should be for IPython, so we can activate inputhook accordingly
    gui = backend2gui.get(backend, None)
    return gui, backend


def is_interactive_backend(backend):
    """ Check if backend is interactive """
    matplotlib = sys.modules['matplotlib']
    required_version = (3, 9)
    installed_version = (
        _get_major_version(matplotlib),
        _get_minor_version(matplotlib)
    )

    if installed_version >= required_version:
        interactive_bk = matplotlib.backends.backend_registry.list_builtin(matplotlib.backends.BackendFilter.INTERACTIVE)
        non_interactive_bk = matplotlib.backends.backend_registry.list_builtin(matplotlib.backends.BackendFilter.NON_INTERACTIVE)
    else:
        from matplotlib.rcsetup import interactive_bk, non_interactive_bk  # @UnresolvedImport

        # Convert mixed-case back-end names (TkAgg, ...) to lowercase
        interactive_bk = [bk.lower() for bk in interactive_bk]
        non_interactive_bk = [bk.lower() for bk in non_interactive_bk]

    if backend in interactive_bk:
        return True
    elif backend in non_interactive_bk:
        return False

    return matplotlib.is_interactive()


def patch_use(enable_gui_function):
    """ Patch matplotlib function 'use' """
    matplotlib = sys.modules['matplotlib']
    def patched_use(*args, **kwargs):
        matplotlib.real_use(*args, **kwargs)
        gui, backend = find_gui_and_backend()
        enable_gui_function(gui)

    matplotlib.real_use = matplotlib.use
    matplotlib.use = patched_use


def patch_is_interactive():
    """ Patch matplotlib function 'use' """
    matplotlib = sys.modules['matplotlib']
    def patched_is_interactive():
        return matplotlib.rcParams['interactive']

    matplotlib.real_is_interactive = matplotlib.is_interactive
    matplotlib.is_interactive = patched_is_interactive


def _get_major_version(module):
    return int(module.__version__.split('.')[0])


def _get_minor_version(module):
    return int(module.__version__.split('.')[1])


def activate_matplotlib(enable_gui_function):
    """Set interactive to True for interactive backends.
    enable_gui_function - Function which enables gui, should be run in the main thread.
    """
    if 'matplotlib' not in sys.modules:
        return False

    matplotlib = sys.modules['matplotlib']
    if not hasattr(matplotlib, 'rcParams'):
        # matplotlib module wasn't fully imported, try later
        return False

    if _get_major_version(matplotlib) >= 3:
        # since matplotlib 3.0, accessing `matplotlib.rcParams` lead to pyplot import,
        # so we need to wait until necessary pyplot attributes will be imported as well
        if 'matplotlib.pyplot' not in sys.modules:
            return False
        pyplot = sys.modules['matplotlib.pyplot']
        if not hasattr(pyplot, 'switch_backend'):
            return False

    try:
        gui, backend = find_gui_and_backend()
    except:
        # matplotlib module wasn't fully imported, try later
        return False

    is_interactive = is_interactive_backend(backend)
    if is_interactive:
        enable_gui_function(gui)
        if not matplotlib.is_interactive():
            sys.stdout.write("Backend %s is interactive backend. Turning interactive mode on.\n" % backend)
        matplotlib.interactive(True)
    else:
        if matplotlib.is_interactive():
            sys.stdout.write("Backend %s is non-interactive backend. Turning interactive mode off.\n" % backend)
        matplotlib.interactive(False)
    patch_use(enable_gui_function)
    patch_is_interactive()
    return True


def flag_calls(func):
    """Wrap a function to detect and flag when it gets called.

    This is a decorator which takes a function and wraps it in a function with
    a 'called' attribute. wrapper.called is initialized to False.

    The wrapper.called attribute is set to False right before each call to the
    wrapped function, so if the call fails it remains False.  After the call
    completes, wrapper.called is set to True and the output is returned.

    Testing for truth in wrapper.called allows you to determine if a call to
    func() was attempted and succeeded."""

    # don't wrap twice
    if hasattr(func, 'called'):
        return func

    def wrapper(*args,**kw):
        wrapper.called = False
        out = func(*args,**kw)
        wrapper.called = True
        return out

    wrapper.called = False
    wrapper.__doc__ = func.__doc__
    return wrapper


def activate_pylab():
    if 'pylab' not in sys.modules:
        return False

    pylab = sys.modules['pylab']
    pylab.show._needmain = False
    # We need to detect at runtime whether show() is called by the user.
    # For this, we wrap it into a decorator which adds a 'called' flag.
    pylab.draw_if_interactive = flag_calls(pylab.draw_if_interactive)
    return True


def activate_pyplot():
    if 'matplotlib.pyplot' not in sys.modules:
        return False

    pyplot = sys.modules['matplotlib.pyplot']
    pyplot.show._needmain = False
    # We need to detect at runtime whether show() is called by the user.
    # For this, we wrap it into a decorator which adds a 'called' flag.
    pyplot.draw_if_interactive = flag_calls(pyplot.draw_if_interactive)
    return True
