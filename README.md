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