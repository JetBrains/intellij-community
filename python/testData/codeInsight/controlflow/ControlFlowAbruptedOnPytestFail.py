def test_fail():
    if True == False:
        pytest.fail()
        print("should be reported as unreachable")
    else:
        return 1