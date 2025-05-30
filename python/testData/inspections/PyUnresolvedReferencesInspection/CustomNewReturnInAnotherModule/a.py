from b import C

c = C()
c.<weak_warning descr="Some members of 'Union[C, Any]' don't have attribute 'foo'">foo</weak_warning>()
