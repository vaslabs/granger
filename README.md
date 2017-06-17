# granger
A patient manager for dentists

The first version is built for aponeurosis management

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
- Provide the git url from the webapp (pending)