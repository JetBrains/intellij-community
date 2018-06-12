https://docs.pytest.org/en/latest/fixture.html

```
@pytest.fixture
def foo():pass

# Somehwere in the code

def test_test(foo):pass
# "foo" is fixture here: pytest calls foo() and provides its return value.
```

PyCharm:
* Disables "unused" and "hides" inspections
* Provides reference (navigate, find usage, rename etc)
* Provides type for foo


