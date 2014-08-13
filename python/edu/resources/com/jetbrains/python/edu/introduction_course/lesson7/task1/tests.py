from test_helper import run_common_tests, failed, get_task_windows, passed


def test_window():
    window = get_task_windows()[0]
    if "fun" in window:
        passed()
    else:
        failed("Name your function 'fun'")


def test_window1():
    window = get_task_windows()[0]
    if "def " in window:
        passed()
    else:
        failed("Use 'def' keyword to define a function")


def test_column():
    window = get_task_windows()[0]
    if ":" in window:
        passed()
    else:
        failed("Don't forget about column at the end of statement")

if __name__ == '__main__':
    run_common_tests()
    test_window()
    test_column()
    test_window1()