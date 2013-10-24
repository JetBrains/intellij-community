from nose.tools import assert_equal, ok_
from nose import tools
import nose.tools


def test():
    ok_('foo')
    assert_equal('foo', 'bar')
    tools.assert_equal('foo', 'bar')
    nose.tools.assert_equal('foo', 'bar')

    <error descr="Unresolved reference 'foo'">foo</error>('foo')
    tools.<warning descr="Cannot find reference 'foo' in '__init__.py'">foo</warning>('foo')
    nose.tools.<warning descr="Cannot find reference 'foo' in '__init__.py'">foo</warning>('foo')


