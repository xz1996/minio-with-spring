package com.example.minio;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.EventType;
import io.minio.messages.Item;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import java.util.LinkedList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class MinioApplicationTests {

  private static final String BUCKET_NAME = "bucket1";
  private static final String OBJECT_NAME = "object1";
  private static final String SQS_ARN = "arn:minio:sqs::RABBITMQ:amqp";

  @Autowired private MinioClient minioClient;

  @SneakyThrows
  @Test
  void testBucketOperation() {
    if (minioClient.bucketExists(BUCKET_NAME)) {
      log.info("{} already exists.", BUCKET_NAME);
    } else {
      minioClient.makeBucket(BUCKET_NAME);
    }
  }

  @Test
  void testObjectOperation() throws Exception {
    log.info("Put object:[{}] into bucket:[{}]", OBJECT_NAME, BUCKET_NAME);
    minioClient.putObject(
        BUCKET_NAME, OBJECT_NAME, "HELP.md", null);

    log.info("List objects of bucket:[{}]", BUCKET_NAME);
    Iterable<Result<Item>> results = minioClient.listObjects(BUCKET_NAME);
    for (Result<Item> result : results) {
      Item item = result.get();
      log.info("Object name:[{}]", item.objectName());
    }
  }

  @Test
  void testBucketNotification() throws Exception {

    List<EventType> eventList = new LinkedList<>();
    eventList.add(EventType.OBJECT_CREATED_PUT);
    eventList.add(EventType.OBJECT_REMOVED_DELETE);

    // Currently minio server only supports QueueConfiguration
    QueueConfiguration queueConfiguration = new QueueConfiguration();
    queueConfiguration.setQueue(SQS_ARN);
    queueConfiguration.setEvents(eventList);

    queueConfiguration.setId("testQ");
    queueConfiguration.setPrefixRule("*");

    List<QueueConfiguration> queueConfigurationList = new LinkedList<>();
    queueConfigurationList.add(queueConfiguration);

    NotificationConfiguration config = new NotificationConfiguration();
    config.setQueueConfigurationList(queueConfigurationList);

    minioClient.setBucketNotification(BUCKET_NAME, config);
  }
}
