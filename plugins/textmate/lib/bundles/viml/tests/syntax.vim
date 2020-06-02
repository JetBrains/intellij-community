:syntax case match
:syntax case ignore
:syntax spell notoplevel
:syntax keyword   Type   int long char
:syntax keyword   Type   contained int long char
:syntax keyword   Type   int long contained char
:syntax keyword   Type   int long char contained=aasf,@a
:syntax match Thing "# [^#]\+ #" contains=@ThingMembers
:syntax cluster ThingMembers contains=ThingMember1,ThingMember2
:syntax region Comment  start="/\*"  end="\*/"
:syntax region String   start=+"+    end=+"+	 skip=+\\"+
:syntax match Character /'.'/hs=s+1,he=e-1
:syn region htmlRef start=+<a>+ end=+</a>+ keepend contains=htmlItem,htmlScript
:syntax match Thing "# [^#]\+ #" contains=@ThingMembers
:syntax cluster ThingMembers contains=ThingMember1,ThingMember2
:syntax include @Pod <sfile>:p:h/pod.vim [aa]
:syntax sync maxlines=100
:syntax sync ccomment maxlines=500
:syntax sync linebreaks=1
:syntax sync fromstart
:syntax sync ccomment
:syntax sync ccomment javaComment
:syntax sync minlines=50
:syntax sync match sync-group-name grouphere group-name +patt "  asfasf" ern+
:syntax sync match sync-group-name groupthere group-name "pattern"
:syntax sync match ..
:syntax sync region ..
:syntax sync linecont pattern
:syntax sync maxlines=100
:syntax sync clear
:syntax sync clear sync-group-name
:syn match ifstart "\<if.*"	  nextgroup=ifline skipwhite skipempty
:syn match ifline  "[^ \t].*" nextgroup=ifline skipwhite skipempty contained
:syn match ifline  "endif"	contained
:syntax match Thing "# [^#]\+ #" contains=@ThingMembers
:syntax cluster ThingMembers contains=ThingMember1,ThingMember2
:syntax keyword A aaa
:syntax keyword B bbb
:syntax cluster AandB contains=A
:syntax match Stuff "( aaa bbb )" contains=@AandB
:syntax cluster AandB add=B	  " now both keywords are matched in Stuff
:syntax keyword A aaa
:syntax keyword B bbb
:syntax cluster SmallGroup contains=B
:syntax cluster BigGroup contains=A,@SmallGroup
:syntax match Stuff "( aaa bbb )" contains=@BigGroup
:syntax cluster BigGroup remove=B	" no effect, since B isn't in BigGroup
:syntax cluster SmallGroup remove=B	" now bbb isn't matched within Stuff
:syntax include @Pod <sfile>:p:h/pod.vim
:syntax region perlPOD start="^=head" end="^=cut" contains=@Pod
