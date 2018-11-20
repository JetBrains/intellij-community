from _pydevd_bundle.pydevd_io import IORedirector


def test_io_redirector():

    class MyRedirection1(object):
        pass

    class MyRedirection2(object):
        pass

    # Check that we don't fail creating the IORedirector if the original
    # doesn't have a 'buffer'.
    IORedirector(MyRedirection1(), MyRedirection2(), wrap_buffer=True)
