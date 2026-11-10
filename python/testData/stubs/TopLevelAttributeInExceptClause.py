try:
    import threading

    GUARDED = 1
except ImportError:
    threading = None
