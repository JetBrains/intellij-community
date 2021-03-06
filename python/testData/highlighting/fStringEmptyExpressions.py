f'{<error descr="Expression expected">}</error>'
f'{<error descr="'}' expected"><error descr="Expression expected">'</error></error>
f'{<EOLError descr="Type conversion, ':' or '}' expected"></EOLError><EOLError descr="Expression expected"></EOLError><EOLError descr="' expected"></EOLError>
f'{<error descr="Expression expected">!</error>r}'
f'{<error descr="Expression expected">:</error>2.3}'
f'{42:2.{<error descr="Expression expected">}</error>}'
f'{<error descr="Expression expected">  </error>}'
f'{42:{<error descr="Expression expected"> </error>}}'
f'{<error descr="Expression expected">  </error>:{<error descr="Expression expected">  </error><error descr="'}' expected">'</error>
f'{<error descr="Expression expected">    </error>!r:{<error descr="Expression expected">   </error>:42}}'