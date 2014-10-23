# This file is meant to be run inside lldb as a command after
# the attach_linux.dylib dll has already been loaded to settrace for all threads.
def __lldb_init_module(debugger, internal_dict):
    # Command Initialization code goes here
    print('Startup LLDB in Python!')

    try:
        show_debug_info = 0
        is_debug = 0
        target = debugger.GetSelectedTarget()
        if target:
            process = target.GetProcess()
            if process:
                for t in process:
                    # Get the first frame
                    frame = t.GetFrameAtIndex (t.GetNumFrames()-1)
                    if frame:
                        print('Will settrace in: %s' % (frame,))
                        frame.EvaluateExpression("expr (int) SetSysTraceFunc(%s, %s);" % (
                            show_debug_info, is_debug))
    except:
        import traceback;traceback.print_exc()
