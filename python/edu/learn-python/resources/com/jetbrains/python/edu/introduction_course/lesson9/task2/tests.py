from test_helper import run_common_tests, failed, passed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "datetime" in window:
        passed()
    else:
        failed("Use datetime module")

if __name__ == '__main__':
    run_common_tests()
    test_window()
