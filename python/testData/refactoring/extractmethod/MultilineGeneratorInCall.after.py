def test_():
    items = ["type:abc"]
    assert set(
        extracted(items)
    )


def extracted(items_new):
    return (item.split(":")[0]
            for item in items_new
            if not item.startswith("type:"))
