from test_helper import run_common_tests, failed, passed, get_task_windows


def test_value():
    window = get_task_windows()[0]

    if "monty_python.upper()" in window:
        passed()
    else:
        failed("Use upper() method")

if __name__ == '__main__':
    run_common_tests()
    test_value()

