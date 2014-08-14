from test_helper import test_is_not_empty, test_is_initial_text, test_window_text_deleted, passed, failed, import_task_file


def test_is_identifier():
    try:
        import_task_file()
    except NameError:
        passed()
        return
    except SyntaxError:
        failed("Used invalid identifier")
        return
    failed("Use undefined variable")


if __name__ == '__main__':
    error_text = "You should type undefined variable here"

    test_is_not_empty()
    test_is_initial_text()
    test_window_text_deleted(error_text)
    test_is_identifier()
