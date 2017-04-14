from nose.plugins.attrib import attr

def test_fast():
    pass

@attr('slow')
def test_Slow():
    pass