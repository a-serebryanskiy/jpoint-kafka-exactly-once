# Репозиторий для примеров кода к докладу на JPoint 2025

Ссылка на доклад: https://jpoint.ru/talks/8fc4127d2e3142e99453d930231970e9/?referer=%2Fpersons%2Fd31fc06d71f7421bb73c8824859a5f6a%2F

# Как пользоваться этим репозиторием

1. Если у вас еще нет кластера Kafka, то поднять его локально, используя [docker-compose.yaml](./docker-compose.yml)
В нем есть Kafka 3.4, а также закоменченная Kafka 3.9 - без зукипера.
2. Открывайте нужный файл в IDE и запускайте его, все должно работать.

Если не работает, пишите мне: t.me/aserebryanskiy

# Какие файлы есть в репозитории

## Иллюстрация бага в транзакциях Kafka

Баг, который будет пофикшен в рамках KIP-890 в Kafka 4.0

Файл - [Kip890Illustration.java](./src/main/java/tech/ydb/topics/jpoint2025/Kip890Illustration.java)

## Сравнение производительности Kafka Streams и Flink в режиме Exactly Once

В пакете [frameworkcomparison](./src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison) лежат файлики, чтобы залить 10Gb данных в топик и сравнить производительность
двух фреймворков при работе в режиме Exactly Once.

Как запустить:
1. Сначала запустить [LoadGenerator.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/LoadGenerator.java), чтобы сгенерировать данные в топик источник.
2. Выставить в приложении [MeasureLatencyConsumer.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/MeasureLatencyConsumer.java) переменную `TOPIC` в значение для теста флинка или кафка стримс.
3. Запустить [MeasureLatencyConsumer.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/MeasureLatencyConsumer.java)
4. Запустить [FlinkTest.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/FlinkTest.java) или [KafkaStreamsTest.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/KafkaStreamsTest.java)

Нагрузка очень простая - вычитать данные из топика, положить их в стейт и записать в целевой топик. 

Замеряется время от начала обработки сообщения фреймворком до его вычитки приложением [MeasureLatencyConsumer.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/MeasureLatencyConsumer.java).
Сделано именно так, чтобы дождаться коммита транзакции в кафке, так как у consumer-а стоит настройка `isolation.mode=read_committed`.

Файлы:
- [LoadGenerator.java](./src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/LoadGenerator.java) - генератор нагрузки в топик
- [FlinkTest.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/FlinkTest.java) - тест флинка
- [KafkaStreamsTest.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/KafkaStreamsTest.java) - тест Kafka Streams
- [MeasureLatencyConsumer.java](src/main/java/tech/ydb/topics/jpoint2025/frameworkscomparison/MeasureLatencyConsumer.java) - приложение, замеряющее нагрузку

Можно поиграть с параллелизмом FlinkTest и временем коммита, но у меня так и не получилось добиться latency хотя бы сравнимой с Kafka Streams.  