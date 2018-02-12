def test(name: str) -> int:
    assert name is not None, f'{name} is None'
#                                 <ref>
    return len(name)
