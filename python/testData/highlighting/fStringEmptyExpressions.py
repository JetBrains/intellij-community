f'{<error descr="expression expected">}</error>'
f'{<error descr="expression expected"><error descr="} expected">'</error></error>
f'{<EOLError descr="type conversion, : or } expected"></EOLError><EOLError descr="expression expected"></EOLError><EOLError descr="' expected"></EOLError>
f'{<error descr="expression expected">!</error>r}'
f'{<error descr="expression expected">:</error>2.3}'
f'{42:2.{<error descr="expression expected">}</error>}'
f'{<error descr="expression expected">  </error>}'
f'{42:{<error descr="expression expected"> </error>}}'
f'{<error descr="expression expected">  </error>:{<error descr="expression expected">  </error><error descr="} expected">'</error>
f'{<error descr="expression expected">    </error>!r:{<error descr="expression expected">   </error>:42}}'