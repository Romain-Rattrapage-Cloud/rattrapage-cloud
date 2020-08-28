FROM rattrapage:cloud

WORKDIR rattrapage-cloud-master

RUN ./mvnw spring-boot:run
