from test_helper import run_common_tests, failed, passed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "from " in window:
        passed()
    else:
        failed("Use from my_module import hello_world")

if __name__ == '__main__':
    run_common_tests()
    test_window()
