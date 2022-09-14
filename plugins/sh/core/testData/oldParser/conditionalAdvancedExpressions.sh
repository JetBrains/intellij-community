[ -z a ]
[ a ]
[ $a ]
[ $(a) = a ]
[ `echo a` ]
[ \${a} ]
[ a  ] 
[ a  ]
[[ $(a)  ]]

if [[ $str == *condition* ]]
then
	echo "String "$str has the word \"condition\"
fi

if [ `whoami` != 'root' ]; then
	echo "Executing the installer script"
fi

if [ $? -eq 0 ] ; then
	echo "Machine is giving ping response"
fi

if [ ! -z $ip ]
then
	echo "IP Address is empty"
fi

if [ $first -eq 0 ] && [ $second -eq 0 ]
then
	echo "$first is lesser than $second"
fi

if [[ "$conf_branch" = r/*/* || "$conf_branch" != r/* && "$conf_branch" = */* ]]; then
  echo "Test output"
fi