FROM eclipse-temurin:24-jre
COPY build/libs/cart_n_order-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
