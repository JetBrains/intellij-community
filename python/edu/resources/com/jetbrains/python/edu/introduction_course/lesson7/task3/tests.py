from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window1():
    window = get_task_windows()[0]
    if "1" in window:
        passed()
    else:
        failed("Initialize b with 1")


def test_window2():
    window = get_task_windows()[1]
    if "b" in window and "a" in window:
        passed()
    else:
        failed("Update b with a + b")

def test_window3():
    window = get_task_windows()[1]
    if "tmp_var" in window:
        passed()
    else:
        failed("Update a with tmp_var")


if __name__ == '__main__':
    run_common_tests()
    test_window1()
    test_window2()
    test_window3()