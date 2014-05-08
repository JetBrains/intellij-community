from pydev_imports import SimpleXMLRPCServer
from pydev_ipython.inputhook import get_inputhook, set_return_control_callback
import select
import sys

select_fn = select.select
if sys.platform.startswith('java'):
    select_fn = select.cpython_compatible_select

class InputHookedXMLRPCServer(SimpleXMLRPCServer):
    ''' An XML-RPC Server that can run hooks while polling for new requests.

        This code was designed to work with IPython's inputhook methods and
        to allow Debug framework to have a place to run commands during idle
        too.
    '''
    def __init__(self, *args, **kwargs):
        SimpleXMLRPCServer.__init__(self, *args, **kwargs)
        # Tell the inputhook mechanisms when control should be returned
        set_return_control_callback(self.return_control)
        self.debug_hook = None
        self.return_control_osc = False

    def return_control(self):
        ''' A function that the inputhooks can call (via inputhook.stdin_ready()) to find 
            out if they should cede control and return '''
        if self.debug_hook:
            # Some of the input hooks check return control without doing
            # a single operation, so we don't return True on every
            # call when the debug hook is in place to allow the GUI to run
            # XXX: Eventually the inputhook code will have diverged enough
            # from the IPython source that it will be worthwhile rewriting
            # it rather than pretending to maintain the old API
            self.return_control_osc = not self.return_control_osc
            if self.return_control_osc:
                return True
        r, unused_w, unused_e = select_fn([self], [], [], 0)
        return bool(r)

    def setDebugHook(self, debug_hook):
        self.debug_hook = debug_hook

    def serve_forever(self):
        ''' Serve forever, running defined hooks regularly and when idle.
            Does not support shutdown '''
        inputhook = get_inputhook()
        while True:
            # Block for default 1/2 second when no GUI is in progress
            timeout = 0.5
            if self.debug_hook:
                self.debug_hook()
                timeout = 0.1
            if inputhook:
                try:
                    inputhook()
                    # The GUI has given us an opportunity to try receiving, normally
                    # this happens because the input hook has already polled the
                    # server has knows something is waiting
                    timeout = 0.020
                except:
                    inputhook = None
            r, unused_w, unused_e = select_fn([self], [], [], timeout)
            if self in r:
                try:
                    self._handle_request_noblock()
                except AttributeError:
                    # Older libraries do not support _handle_request_noblock, so fall
                    # back to the handle_request version
                    self.handle_request()
                # Running the request may have changed the inputhook in use
                inputhook = get_inputhook()

    def shutdown(self):
        raise NotImplementedError('InputHookedXMLRPCServer does not support shutdown')
