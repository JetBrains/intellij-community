class TestScrollToBottom:
    def test_failing_scroll_to_bottom(self):
        fail_with_stack_trace()

    def test_passing_after_failure_01(self):
        pass

    def test_passing_after_failure_02(self):
        pass

    def test_passing_after_failure_03(self):
        pass

    def test_passing_after_failure_04(self):
        pass

    def test_passing_after_failure_05(self):
        pass

    def test_passing_after_failure_06(self):
        pass

    def test_passing_after_failure_07(self):
        pass

    def test_passing_after_failure_08(self):
        pass

    def test_passing_after_failure_09(self):
        pass

    def test_passing_after_failure_10(self):
        pass


def fail_with_stack_trace():
    raise AssertionError("terminal should show this failure at bottom")
