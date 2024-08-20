# The Chappie server

#### To publish
`mvn clean install -Dnative`

`docker build -f src/main/docker/Dockerfile.native-micro -t chappie/chappie-server .`

`docker commit 2f2....0056 quay.io/chappie/chappie-server`

`docker push quay.io/chappie/chappie-server:0.0.1`