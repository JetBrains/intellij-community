from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window():
    window = get_task_windows()[0]
    if "phone_book" in window and "Jane" in window:
        passed()
    else:
        failed("Use indexing e.g. dct[key]")

if __name__ == '__main__':
    run_common_tests("Use indexing e.g. dct[key]")
    test_window()
