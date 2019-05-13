from contextlib import nested
with nested(patch('Package.ModuleName.ClassName'),
            patch('Package.ModuleName.ClassName2', TestUtils.MockClass2)) as (MockClass1, MockClass2):
    MockClass1.test.return_value = True
#     <ref>
    MockClass2.test.return_value = True