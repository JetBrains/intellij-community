'#%(language)s has %(#)03d quote types.' % {'language': "Python", "#": 2}  #ok
'%d %s' % <warning descr="Too few arguments for format string">5</warning>  #Too few arguments for format string
'Hello world' % <warning descr="Too many arguments for format string">25</warning> #Too many arguments for format string
"%(name)f(name)" % {'name': 23.2} #ok
"%()s" % {'': "name"} #ok
<warning descr="Format specifier character missing">'test%(name)'</warning> % {'name': 23} #There are no format specifier character
'work%*d' % (2, 34) #ok
<warning descr="Can't use '*' in formats when using a mapping">'work%(name)*d'</warning> % (12, 32) #Can't use '*' in formats when using a mapping
'%*.*d' % (2, 5, 5) #ok
'%*.*d' % (<warning descr="Too few arguments for format string">2, 4</warning>) #Too few arguments for format string
'%*.*d' % (<warning descr="Too many arguments for format string">2, 4, 5, 6</warning>) #Too many arguments for format string
<warning descr="Format specifier character missing">'%**d'</warning> % (2, 5) #There are no format specifier character
<warning descr="Too few mapping keys">'%(name1)s %(name2)s (name3) %s'</warning> % {'name1': 'a', 'name2': 'b', 'name3': 'c'} #Too few mapping keys
<warning descr="Too few mapping keys">'%(name1s'</warning> % {'name1': 'a'} #Too few mapping keys
'%%%(name)ld' % {'name': 12} #ok
"%(name)f(name)" % <warning descr="Format requires a mapping">23.2</warning> #Format requires a mapping
"%(name)f(name)" % (<warning descr="Format requires a mapping">23.2</warning>) #Format requires a mapping
'%d%d' % <warning descr="Format doesn't require a mapping">{'name1': 2, 'name2': 3}</warning> #Format doesn't require a mapping
'%12.2f' % 2.74 #ok
'Hello world' % () #ok
'Hello world' % [] #ok
'Hello world' % {} #ok
'%d%d' % ((5), (5)) #ok
"%(name)d %(name)d" % {"name": 43} #ok
"%(name)d" % {'a': 4, "name": 5} #ok
'%% name %(name)c' % <warning descr="Key 'name' has no following argument">{'a': 4}</warning> #Key 'name' has no following argument
'%d %u %f %F %s %r' % (2, 3, 4.1, 4.0, "name", "str") #ok
'%d %d %d' % (4, <warning descr="Unexpected type str">"a"</warning>, <warning descr="Unexpected type str">"b"</warning>) #Unexpected type
'%f %f %f' % (4, 5, <warning descr="Unexpected type str">"test"</warning>) #Unexpected type
'%d' % <warning descr="Unexpected type str">"name"</warning> #Unexpected type
m = {'language': "Python", "#": 2}
'#%(language)s has %(#)03d quote types.' % m  #ok
i = "test"
'%(name)s' % {'name': i}  #ok
'%s' % i  #ok
'%f' % <warning descr="Unexpected type str">i</warning>  #Unexpected type
'%f' % (2 * 3 + 5)  #ok
s = "%s" % "a".upper() #ok
x = ['a', 'b', 'c']
print "%d: %s" % (len(x), ", ".join(x)) #ok
m = [1, 2, 3, 4, 5]
"%d" % m[0]  #ok
"%d %s" % (m[0], m[4])  #ok
"%s" % m  #ok
"%s" % m[1:3]  #ok
"%d" % <warning descr="Unexpected type str">m[1:2]</warning>  #ok
"%d" % <warning descr="Unexpected type str">m</warning>  #Unexpected type
"%d" % <warning descr="Unexpected type str">[]</warning>  #Unexpected type
def greet(all):
    print "Hello %s" % ("World" if all else "Human") #ok
"%s" % [x + 1 for x in [1, 2, 3, 4]]  #ok
"%s" % [x + y for x in []]  #ok
"%s" % []  #ok
"%f" % <warning descr="Unexpected type str">[x + 1 for x in [1, 2, 3, 4]]</warning>  #Unexpected type
"%d %d" % (3, 5)  #ok
"Hello %s %s" % tuple(['world', '!'])  #ok

def foo(a):
    if a == 1:
        return "a", "b"
    else:
        return "c", "d"
print "%s" % <warning descr="Too many arguments for format string">foo(1)</warning>  #Too many arguments for format string

print("| [%(issue_id)s|http://youtrack.jetbrains.net/issue/%(issue_id)s] (%(issue_type)s)|%(summary)s|" % (<warning descr="Format requires a mapping">issue_id, issue_type, summary</warning>)) #Format requires a mapping (PY-704)

my_list = list()
for i in range(0,3):
    my_list.append( ("hey", "you") )

for item in my_list:
    print '%s %s' % item   # ok (PY-734)

def bar():
    return None
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>   #Too few arguments for format string

"%s" % {} # ok; str() works
"%s" % {'a': 1, 'b': 2} # ok, no names in template and arg counts don't match
"%s" % object() # ok, str() works
"foo" % {'bar':1, 'baz':2} # ok: empty template that could use names

a = ('a', 1) if 1 else ('b', 2)
"%s is %d" % a # ok, must infer unified tuple type
#PY-3064, because original type of a is tuple, not list
a = (1,2,3)
print '%d:%d' % a[:2]
print '%d:%d' % <warning descr="Too few arguments for format string">a[1:2]</warning>

string = "qwerty"
print '%d:%d' % <warning descr="Too few arguments for format string"><warning descr="Unexpected type str">string[:2]</warning></warning>
print '%s:%s' % <warning descr="Too few arguments for format string">string[:2]</warning>
print '%s' % string[:2]
print '%d' % <warning descr="Unexpected type str">string[:2]</warning>

my_tuple = (1,2,3,4,5,6,7,8)
print '%d, %d' % <warning descr="Too many arguments for format string">my_tuple[:7:3]</warning>
print '%d, %d, %d' % my_tuple[:7:3]
print '%d, %d, %d, %d' % <warning descr="Too few arguments for format string">my_tuple[:7:3]</warning>

# PY-12801
print '%d %s' % ((42,) + ('spam',))
print '%d %s' % (<warning descr="Unexpected type str">('ham',) + ('spam',)</warning>)
print '%d %s' % (<warning descr="Too few arguments for format string">(42,) + ()</warning>)
print '%d' % (<warning descr="Too many arguments for format string">(42,) + ('spam',)</warning>)

# PY-11274
import collections
print '%(foo)s' % collections.OrderedDict(foo=None)

class MyDict(collections.Mapping):
    def __getitem__(self, key):
        return 'spam'

    def __iter__(self):
        yield 'spam'

    def __len__(self):
        return 1

print '%(foo)s' % MyDict()

foo = {1, 2, 3}
print('%s %s %s' % <warning descr="Too few arguments for format string">foo</warning>)

'%s %s %s' % <warning descr="Too few arguments for format string">(x for x in range(10))</warning>

