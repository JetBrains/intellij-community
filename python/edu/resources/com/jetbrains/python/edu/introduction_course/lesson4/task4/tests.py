from test_helper import run_common_tests, passed, failed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "len(" in window:
        passed()
    else:
        failed("Use len() function")


if __name__ == '__main__':
    run_common_tests("Use len() function")
