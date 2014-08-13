from test_helper import run_common_tests, passed, failed, import_task_file, get_task_windows


def test_division():
    file = import_task_file()
    if file.division == 4.5:
        passed()
    else:
        failed("Use / operator")

def test_remainder():
    file = import_task_file()
    if file.remainder == 1.0:
        passed()
    else:
        failed("Use % operator")

def test_windows():
    windows = get_task_windows()

    if "number" in windows[0] and "number" in windows[1]:
        passed()
    else:
        failed("Use % operator")


if __name__ == '__main__':
    run_common_tests("Use / and % operators")

    test_division()
    test_remainder()