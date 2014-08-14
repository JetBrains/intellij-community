from test_helper import run_common_tests, failed, get_task_windows, passed


def test_window():
    window = get_task_windows()[0]
    if "square" in window and "def " in window:
        passed()
    else:
        failed("Name your function 'square'")


def test_column():
    window = get_task_windows()[0]
    if ":" in window:
        passed()
    else:
        failed("Don't forget about column at the end of statement")

if __name__ == '__main__':
    run_common_tests()
    test_column()
    test_window()