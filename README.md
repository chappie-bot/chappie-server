# The Chappie server

#### To publish
`mvn clean install -Dnative`

`docker build -f src/main/docker/Dockerfile.native-micro -t chappie/chappie-server:0.0.1 .`

`docker run -i --rm -p 4315:4315 chappie/chappie-server:0.0.1`

`docker ps`

`docker commit 2f2....0056 quay.io/chappie/chappie-server:0.0.1`

`docker push quay.io/chappie/chappie-server:0.0.1`