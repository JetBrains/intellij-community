#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import threading

from _pydev_bundle.pydev_is_thread_alive import is_thread_alive


def test_id_thread_alive():
    start_event = threading.Event()
    end_event = threading.Event()

    def worker():
        start_event.set()
        end_event.wait()

    t = threading.Thread(target=worker)

    t.start()
    start_event.wait()

    assert is_thread_alive(t)

    end_event.set()
    t.join()

    assert not is_thread_alive(t)
