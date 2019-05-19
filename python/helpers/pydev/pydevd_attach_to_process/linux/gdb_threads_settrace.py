# This file is meant to be run inside GDB as a command after 
# the attach_linux.so dll has already been loaded to settrace for all threads.
if __name__ == '__main__':
    #print('Startup GDB in Python!')

    try:
        show_debug_info = 0
        is_debug = 0
        for t in list(gdb.selected_inferior().threads()):
            t.switch()
            if t.is_stopped():
                #print('Will settrace in: %s' % (t,))
                gdb.execute("call (int)SetSysTraceFunc(%s, %s)" % (
                    show_debug_info, is_debug))
    except:
        import traceback;traceback.print_exc()
