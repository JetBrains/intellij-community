from IPython.frontend.prefilterfrontend import PrefilterFrontEnd
from pydev_console_utils import Null
import sys
original_stdout = sys.stdout
original_stderr = sys.stderr


#=======================================================================================================================
# PyDevFrontEnd
#=======================================================================================================================
class PyDevFrontEnd(PrefilterFrontEnd):


    def __init__(self, *args, **kwargs):        
        PrefilterFrontEnd.__init__(self, *args, **kwargs)
        #Disable the output trap: we want all that happens to go to the output directly
        self.shell.output_trap = Null()
        self._curr_exec_lines = []
        self._continuation_prompt = ''
        
        
    def capture_output(self):
        pass
    
    
    def release_output(self):
        pass
    
    
    def continuation_prompt(self):
        return self._continuation_prompt
    
    
    def write(self, txt, refresh=True):
        original_stdout.write(txt)
        

    def new_prompt(self, prompt):
        self.input_buffer = ''
        #The java side takes care of this part.
        #self.write(prompt)
        
        
    def show_traceback(self):
        import traceback;traceback.print_exc()
        
        
    def write_out(self, txt, *args, **kwargs):
        original_stdout.write(txt)
    
    
    def write_err(self, txt, *args, **kwargs):
        original_stderr.write(txt)
        
        
    def getNamespace(self):
        return self.shell.user_ns


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
    
    
    def addExec(self, line):
        if self._curr_exec_lines:
            if not line:
                self._curr_exec_lines.append(line)

                #Would be the line below, but we've set the continuation_prompt to ''.
                #buf = self.continuation_prompt() + ('\n' + self.continuation_prompt()).join(self._curr_exec_lines)
                buf = '\n'.join(self._curr_exec_lines)

                self.input_buffer = buf + '\n'
                if self._on_enter():
                    del self._curr_exec_lines[:]
                    return False #execute complete (no more)

                return True #needs more
            else:
                self._curr_exec_lines.append(line)
                return True #needs more

        else:

            self.input_buffer = line
            if not self._on_enter():
                #Did not execute
                self._curr_exec_lines.append(line)
                return True #needs more

            return False #execute complete (no more)

    def update(self, globals, locals):
        locals['_oh'] = self.shell.user_ns['_oh']
        locals['_ip'] = self.shell.user_ns['_ip']
        self.shell.user_global_ns = globals
        self.shell.user_ns = locals

    def is_automagic(self):
        if self.ipython0.rc.automagic:
            return True
        else:
            return False

    def get_greeting_msg(self):
        return 'PyDev console: using IPython 0.10\n'

