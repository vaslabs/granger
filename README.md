# granger

[![Join the chat at https://gitter.im/vaslabs-granger/Lobby](https://badges.gitter.im/vaslabs-granger/Lobby.svg)](https://gitter.im/vaslabs-granger/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
A patient manager for dentists

The first version is built for root canal treatment (RCT) management

# Build
```
sbt
docker
dockerComposeUp
```

# Release process

- Build a separate docker for each user
- Each container build will have a unique pair of RSA keys.
- Use the public key to register the customer to a hosted git repo
- Provide the git url from the webapp


# Information for contributors

- When you make a front-end change, please consult http://materializecss.com/
- Do
```bash
docker build -t git-server git-server/ to build a new git server docker
```
- To build a granger docker do
```
sbt docker
```
- To bring the dockers up do
```
docker-compose up
```
Then go to granger with
```
docker exec -it <granger_docker_container_hash> bash
```
and run

```
ssh -oHostKeyAlgorithms='ssh-rsa' git@git-server
```

Now browse localhost:8080 . It should show a git uri placeholder. Put
```
ssh://git@git-server/git-server/repos/granger_repo
```
Press submit and put the ssh key in the git server docker (/home/git/.ssh/authorized_keys) and copy the id_rsa.pub key from granger docker to
/git-server/keys/