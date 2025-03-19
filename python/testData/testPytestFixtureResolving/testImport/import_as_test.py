from another_fixture import some_fixture as sf

def test_fixture(s<caret>f):
    assert sf == 'another_fixture'
