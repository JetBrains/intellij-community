try:
    from IPython.terminal.interactiveshell import TerminalInteractiveShell
except ImportError:
    from IPython.frontend.terminal.interactiveshell import TerminalInteractiveShell
from IPython.utils import io
import sys
import codeop, re
original_stdout = sys.stdout
original_stderr = sys.stderr
from IPython.core import release


#=======================================================================================================================
# _showtraceback
#=======================================================================================================================
def _showtraceback(*args, **kwargs):
    import traceback;traceback.print_exc()
    
    
    
#=======================================================================================================================
# PyDevFrontEnd
#=======================================================================================================================
class PyDevFrontEnd:

    version = release.__version__


    def __init__(self, *args, **kwargs):        
        #Initialization based on: from IPython.testing.globalipapp import start_ipython
        
        self._curr_exec_line = 0
        # Store certain global objects that IPython modifies
        _displayhook = sys.displayhook
        _excepthook = sys.excepthook
    
        # Create and initialize our IPython instance.
        shell = TerminalInteractiveShell.instance()
    
        shell.showtraceback = _showtraceback
        # IPython is ready, now clean up some global state...
        
        # Deactivate the various python system hooks added by ipython for
        # interactive convenience so we don't confuse the doctest system
        sys.displayhook = _displayhook
        sys.excepthook = _excepthook
    
        # So that ipython magics and aliases can be doctested (they work by making
        # a call into a global _ip object).  Also make the top-level get_ipython
        # now return this without recursively calling here again.
        get_ipython = shell.get_ipython
        try:
            import __builtin__
        except:
            import builtins as __builtin__
        __builtin__._ip = shell
        __builtin__.get_ipython = get_ipython
        
        # We want to print to stdout/err as usual.
        io.stdout = original_stdout
        io.stderr = original_stderr
    
        
        self._curr_exec_lines = []
        self.ipython = shell


    def update(self, globals, locals):
        ns = self.ipython.user_ns

        for ind in ['_oh', '_ih', '_dh', '_sh', 'In', 'Out', 'get_ipython', 'exit', 'quit']:
            locals[ind] = ns[ind]

        self.ipython.user_global_ns.clear()
        self.ipython.user_global_ns.update(globals)
        self.ipython.user_ns = locals

        if hasattr(self.ipython, 'history_manager') and hasattr(self.ipython.history_manager, 'save_thread'):
            self.ipython.history_manager.save_thread.pydev_do_not_trace = True #don't trace ipython history saving thread

    def complete(self, string):
        if string:
            return self.ipython.complete(string)
        else:
            return self.ipython.complete(string, string, 0)
    
    
        
    def is_complete(self, string):
        #Based on IPython 0.10.1
         
        if string in ('', '\n'):
            # Prefiltering, eg through ipython0, may return an empty
            # string although some operations have been accomplished. We
            # thus want to consider an empty string as a complete
            # statement.
            return True
        else:
            try:
                # Add line returns here, to make sure that the statement is
                # complete (except if '\' was used).
                # This should probably be done in a different place (like
                # maybe 'prefilter_input' method? For now, this works.
                clean_string = string.rstrip('\n')
                if not clean_string.endswith('\\'): clean_string += '\n\n' 
                is_complete = codeop.compile_command(clean_string,
                            "<string>", "exec")
            except Exception:
                # XXX: Hack: return True so that the
                # code gets executed and the error captured.
                is_complete = True
            return is_complete
        
        
    def getNamespace(self):
        return self.ipython.user_ns

    
    def addExec(self, line):
        if self._curr_exec_lines:
            self._curr_exec_lines.append(line)

            buf = '\n'.join(self._curr_exec_lines)

            if self.is_complete(buf):
                self._curr_exec_line += 1
                self.ipython.run_cell(buf)
                del self._curr_exec_lines[:]
                return False #execute complete (no more)

            return True #needs more
        else:

            if not self.is_complete(line):
                #Did not execute
                self._curr_exec_lines.append(line)
                return True #needs more
            else:
                self._curr_exec_line += 1
                self.ipython.run_cell(line, store_history=True)
                #hist = self.ipython.history_manager.output_hist_reprs
                #rep = hist.get(self._curr_exec_line, None)
                #if rep is not None:
                #    print(rep)
                return False #execute complete (no more)

    def is_automagic(self):
        return self.ipython.automagic

    def get_greeting_msg(self):
        return 'PyDev console: using IPython %s\n' % self.version

