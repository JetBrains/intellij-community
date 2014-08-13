from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window1():
    window = get_task_windows()[0]
    if "John" in window and "==" in window and "if " in window:
        passed()
    else:
        failed("Use if keyword")


def test_window2():
    window = get_task_windows()[1]
    if "else" in window:
        passed()
    else:
        failed("Use else keyword")


def test_columns():
    windows = get_task_windows()
    if ":" in windows[0] and ":" in windows[1]:
        passed()
    else:
        failed("Don't forget about column at the end")

if __name__ == '__main__':
    run_common_tests("Use if/else keywords")
    test_window1()
    test_window2()
    test_columns()
