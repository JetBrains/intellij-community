def test_exit():
    exit()
    <warning descr="This code is unreachable">print("should be reported as unreachable")</warning>
    <warning descr="This code is unreachable">return True</warning>