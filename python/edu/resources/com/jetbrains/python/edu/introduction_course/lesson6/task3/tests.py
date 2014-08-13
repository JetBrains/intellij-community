from test_helper import run_common_tests, failed, passed, get_task_windows


def test_column():
    window = get_task_windows()[0]
    if ":" in window:
        passed()
    else:
        failed("Don't forget about column at the end")


def test_window():
    window = get_task_windows()[0]
    if "while " in window:
        passed()
    else:
        failed("Use while loop to iterate")


if __name__ == '__main__':
    run_common_tests()
    test_window()
    test_column()