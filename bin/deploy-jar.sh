#!/bin/bash

mvn deploy:deploy-file -Dfile=spectomic.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml