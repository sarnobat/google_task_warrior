# export GMAIL_PASSWORD=password123 && sh run.sh

groovy gcaltaskwarrior.groovy | column -t -s "::" | sort -h
groovy mail.groovy   