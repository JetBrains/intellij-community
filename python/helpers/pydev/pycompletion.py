#!/usr/bin/python
'''
@author Radim Kubacki
'''
import _pydev_imports_tipper
import traceback
import StringIO
import sys
import urllib
import pycompletionserver


#=======================================================================================================================
# GetImports
#=======================================================================================================================
def GetImports(module_name):
    try:
        processor = pycompletionserver.Processor()
        data = urllib.unquote_plus(module_name)
        def_file, completions = _pydev_imports_tipper.GenerateTip(data)
        return processor.formatCompletionMessage(def_file, completions)
    except:
        s = StringIO.StringIO()
        exc_info = sys.exc_info()

        traceback.print_exception(exc_info[0], exc_info[1], exc_info[2], limit=None, file=s)
        err = s.getvalue()
        pycompletionserver.dbg('Received error: ' + str(err), pycompletionserver.ERROR)
        raise


#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    mod_name = sys.argv[1]

    print(GetImports(mod_name))

