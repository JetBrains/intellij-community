${parameter:-`printf "default value"`}
${parameter-`printf "default value"`}

${parameter:-$(printf "default value")}
${parameter-$(printf "default value")}

${parameter:-$((2 + 40))}
${parameter-$((2 + 40))}

${parameter:-$[2 + 40]}
${parameter-$[2 + 40]}

# default value with chars, which may follow a $, needs to be lexed as body token
${parameter-(){}
${parameter-\{\}\(\)}

# IDEA-256714
sp="${base##*[![:space:]]}"