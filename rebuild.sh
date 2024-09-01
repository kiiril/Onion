docker rm $(docker ps -qa)
docker image rm onion
docker build -t onion .