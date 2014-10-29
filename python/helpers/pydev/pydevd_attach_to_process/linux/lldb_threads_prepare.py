# This file is meant to be run inside lldb as a command before
# attaching the debugger to mark process threads as suspended
# to distinguish them from debugger threads later

def __lldb_init_module(debugger, internal_dict):
    import lldb

    try:
        target = debugger.GetSelectedTarget()
        if target:
            process = target.GetProcess()
            if process:
                for thread in process:
                    # print('Marking process thread %d'%thread.GetThreadID())
                    internal_dict['_thread_%d' % thread.GetThreadID()] = True
                    # thread.Suspend()
    except:
        import traceback;traceback.print_exc()
