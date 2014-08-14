from test_helper import run_common_tests, failed, passed, get_task_windows


def test_ASCII():
    windows = get_task_windows()
    for window in windows:
        all_ascii = all(ord(c) < 128 for c in window)
        if not all_ascii:
            failed("Please, use only english characters for this time.")
            return
    passed()

def test_is_alpha():
    window = get_task_windows()[0]
    if window.isaplpha():
      passed()
    else:
      failed("Please, use only english characters for this time.")


if __name__ == '__main__':
    test_ASCII()
    run_common_tests("You should type your name")
    test_is_alpha()


