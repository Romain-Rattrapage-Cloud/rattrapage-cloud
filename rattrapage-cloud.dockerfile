FROM rattrapage:cloud

WORKDIR rattrapage-cloud-master

CMD ./mvnw spring-boot:run
