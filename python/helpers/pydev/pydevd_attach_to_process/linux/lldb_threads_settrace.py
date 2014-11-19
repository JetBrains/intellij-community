# This file is meant to be run inside lldb as a command after
# the attach_linux.dylib dll has already been loaded to settrace for all threads.
def __lldb_init_module(debugger, internal_dict):
    # Command Initialization code goes here
    # print('Startup LLDB in Python!')
    import lldb

    try:
        show_debug_info = 1
        is_debug = 0

        options = lldb.SBExpressionOptions()
        options.SetFetchDynamicValue()
        options.SetTryAllThreads(run_others=False)
        options.SetTimeoutInMicroSeconds(timeout=10000000)

        target = debugger.GetSelectedTarget()
        if target:
            process = target.GetProcess()
            if process:
                for thread in process:
                    # Get the first frame
                    # print('Thread %s, suspended %s\n'%(thread, thread.IsStopped()))

                    if internal_dict.get('_thread_%d' % thread.GetThreadID(), False):
                        process.SetSelectedThread(thread)
                        if not thread.IsStopped():
                            # thread.Suspend()
                            error = process.Stop()

                        frame = thread.GetSelectedFrame()

                        if frame.GetFunctionName() == '__select':
                            # print('We are in __select')
                            # Step over select, otherwise evaluating expression there can terminate thread
                            thread.StepOver()
                            frame = thread.GetSelectedFrame()

                        print('Will settrace in: %s' % (frame,))

                        for f in thread:
                            print(f)

                        res = frame.EvaluateExpression("(int) SetSysTraceFunc(%s, %s)" % (
                            show_debug_info, is_debug), options)
                        error = res.GetError()
                        if error:
                            print(error)

                        thread.Resume()
    except:
        import traceback;traceback.print_exc()
