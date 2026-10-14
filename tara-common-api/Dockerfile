FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/tara-common-api.jar app.jar
EXPOSE 8080
# 프로파일은 env SPRING_PROFILES_ACTIVE 로 정한다(운영 prod).
ENTRYPOINT ["java", "-jar", "app.jar"]
