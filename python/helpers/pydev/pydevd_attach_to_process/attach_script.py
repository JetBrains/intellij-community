def attach(port, host, protocol=''):
    try:
        if protocol:
            from _pydevd_bundle import pydevd_defaults
            pydevd_defaults.PydevdCustomization.DEFAULT_PROTOCOL = protocol

        import pydevd
        pydevd.stoptrace()  # I.e.: disconnect if already connected
        # pydevd.DebugInfoHolder.DEBUG_RECORD_SOCKET_READS = True
        # pydevd.DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS = 3
        # pydevd.DebugInfoHolder.DEBUG_TRACE_LEVEL = 3
        pydevd.settrace(
            port=port,
            host=host,
            stdoutToServer=True,
            stderrToServer=True,
            overwrite_prev_trace=True,
            suspend=False,
            trace_only_current_thread=False,
            patch_multiprocessing=False,
        )
    except:
        import traceback
        traceback.print_exc()
