f'{x:{y:<error descr="Expression fragment inside f-string is nested too deeply">{}</error>}}'
f'{x:{y:<error descr="Expression fragment inside f-string is nested too deeply">{# foo}</error>}}'
f'{x:{y:<error descr="Expression fragment inside f-string is nested too deeply">{z!z}</error>}}'
f'{x:{y:<error descr="Expression fragment inside f-string is nested too deeply">{z:{42}}</error>}}'
f'<error descr="Empty expression fragments are not allowed inside f-strings">{:</error><error descr="Empty expression fragments are not allowed inside f-strings">{:</error><error descr="Expression fragment inside f-string is nested too deeply">{:{}}</error>}}'
f'<error descr="'}' is expected">{x:{y:<error descr="Expression fragment inside f-string is nested too deeply">{z</error></error>'
<error descr="Missing closing quote [']">f'<error descr="'}' is expected">{x:{y:<error descr="Expression fragment inside f-string is nested too deeply">{z</error></error></error>