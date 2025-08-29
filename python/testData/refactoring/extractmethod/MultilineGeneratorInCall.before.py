def test_():
    items = ["type:abc"]
    assert set(
        <selection>item.split(":")[0]
        for item in items
        if not item.startswith("type:")</selection>
    )
