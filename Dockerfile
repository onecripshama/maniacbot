# Стадия сборки
FROM gradle:8.4-jdk17 AS build

WORKDIR /app

# Кэшируем зависимости
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle --no-daemon dependencies

# Копируем проект
COPY . .

# Собираем fat JAR
RUN gradle shadowJar --no-daemon

# Стадия выполнения
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копируем jar из предыдущей стадии
COPY --from=build /app/build/libs/*.jar app.jar

# Открываем порт (так надо, поверьте мне)
EXPOSE 8080

# Запускаем приложение
CMD ["java", "-jar", "app.jar"]
