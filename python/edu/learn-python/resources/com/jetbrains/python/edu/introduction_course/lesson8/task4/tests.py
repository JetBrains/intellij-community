from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window():
    window = get_task_windows()[0]
    if "self" in window:
        passed()
    else:
        failed("Access current variable of the class using self.current")

if __name__ == '__main__':
    run_common_tests()
    test_window()