# MinIO Primer

## What is MinIO

[MinIO](https://min.io/) is a High Performance Object Storage released under Apache License v2.0. It is API compatible with Amazon S3 cloud storage service. Use MinIO to build high performance infrastructure for machine learning, analytics and application data workloads.

## Why choose it

* MinIO is the world's fastest object storage server.
  ![MinIO benchmark](https://min.io/resources/img/home/high-performance.svg)
  With READ/WRITE speeds of 183 GB/s and 171 GB/s on standard hardware, object storage can operate as the primary storage tier for a diverse set of workloads ranging from Spark, Presto, TensorFlow, H2O.ai as well as a replacement for Hadoop HDFS.

* Simple scaling

* No other object store is more Kubernetes-friendly.

* 100% open source under the Apache V2 license.

* The defacto standard for Amazon S3 compatibility.

## Concepts

### [Erasure Code](https://docs.min.io/docs/minio-erasure-code-quickstart-guide.html)

Erasure code is a mathematical algorithm to reconstruct missing or corrupted data.

### [MinIO Client](https://docs.min.io/docs/minio-client-quickstart-guide.html)

MinIO Client (mc) provides a modern alternative to UNIX commands like ls, cat, cp, mirror, diff, find etc. It supports filesystems and Amazon S3 compatible cloud storage service (AWS Signature v2 and v4).

## [Deployment](https://docs.min.io/docs/minio-deployment-quickstart-guide.html)

MinIO in distributed mode can help us setup a highly-available storage system with a single object storage deployment. With distributed MinIO, we can optimally use storage devices, irrespective of their location in a network. For convenient deploying locally, we choose docker compose.

### Basic Running

1. Prerequisites

    * Familiarity with [Docker Compose](https://docs.docker.com/compose/overview/).
    * Docker installed on your machine. Download the relevant installer from [here](https://www.docker.com/community-edition#/download).

2. Run Distributed MinIO on Docker Compose

    docker-compose.yml

    ```yaml
    version: '3.7'

    x-minio-common-config:
        &minio-common-config
        image: minio/minio:RELEASE.2020-06-22T03-12-50Z
        environment:
            MINIO_ACCESS_KEY: minio
            MINIO_SECRET_KEY: minio123
        command: server http://minio{1...4}/data{1...2}
        healthcheck:
            test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
            interval: 30s
            timeout: 20s
            retries: 3

    # starts 4 docker containers running minio server instances. Each
    # minio server's web interface will be accessible on the host at port
    # 9001 through 9004.
    services:
        minio1:
            volumes:
            - data1-1:/data1
            - data1-2:/data2
            ports:
            - "9001:9000"
            << : *minio-common-config

        minio2:
            volumes:
            - data2-1:/data1
            - data2-2:/data2
            ports:
            - "9002:9000"
            << : *minio-common-config

        minio3:
            volumes:
            - data3-1:/data1
            - data3-2:/data2
            ports:
            - "9003:9000"
            << : *minio-common-config

        minio4:
            volumes:
            - data4-1:/data1
            - data4-2:/data2
            ports:
            - "9004:9000"
            << : *minio-common-config

    ## By default this config uses default local driver,
    ## For custom volumes replace with volume driver configuration.
    volumes:
        data1-1:
        data1-2:
        data2-1:
        data2-2:
        data3-1:
        data3-2:
        data4-1:
        data4-2:
    ```

    Then run `docker-compose up`, it will automatically download relevant docker images and run them. Each instance is now accessible on the host at ports 9001 through 9004, proceed to access the Web browser at <http://127.0.0.1:9001/>

    Notes:

    * `http://minio{1...4}/data{1...2}` is syntactic sugar, it equals to `http://minio1/data1, http://minio1/data2, ... , http://minio4/data2`, **minio1, ... , minio4** are service name defined in docker-compose.yml, **data1, data2** are drive locations to persist minio server data.

    * All the nodes running distributed MinIO need to have same access key and secret key for the nodes to connect, and the same command `server http://minio{1...4}/data{1...2}`.

    * You can access the distributed minio server from <http://127.0.0.1:9001> to <http://127.0.0.1:9004>, each node can manage all *4 x 2 = 8* drivers, that means you can upload an object via <http://127.0.0.1:9001> and delete it by <http://127.0.0.1:9004>.

### Extension

1. Reverse Proxy

    We hope to use one endpoint to manage our server, and maybe balance the workloads among nodes. For the sake of that, we need a **reverse proxy**, well, **nginx** is a good choice, you can refer to the official docs: <https://docs.min.io/docs/setup-nginx-proxy-with-minio.html>, I prefer to using docker-compose to enhance it.

    * Add the following content into the `service` block of preceding docker-compose.yml

        ```yaml
        nginx:
            image: nginx:1.19.0
            ports:
            - "9000:9000"
            volumes:
            - ./nginx.conf:/etc/nginx/nginx.conf
        ```

    * Put `nginx.conf` file where the docker-compose.yml is

        ```conf
        user  nginx;
        worker_processes  1;

        error_log  /var/log/nginx/error.log warn;
        pid        /var/run/nginx.pid;

        events {
            worker_connections  1024;
        }

        http {
            include       /etc/nginx/mime.types;
            default_type  application/octet-stream;

            log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                            '$status $body_bytes_sent "$http_referer" '
                            '"$http_user_agent" "$http_x_forwarded_for"';

            access_log  /var/log/nginx/access.log  main;

            sendfile        on;
            #tcp_nopush     on;

            keepalive_timeout  65;

            #gzip  on;

            include /etc/nginx/conf.d/*.conf;

            upstream http_minio {
                server minio1:9000;
                server minio2:9000;
                server minio3:9000;
                server minio4:9000;
            }

            server {
                listen 9000;

                # To allow special characters in headers
                ignore_invalid_headers off;

                # Allow any size file to be uploaded.
                # Set to a value such as 1000m; to restrict file size to a specific value.
                # To disable checking of client request body size, set client_max_body_size to 0.
                client_max_body_size 0;

                # To disable buffering
                proxy_buffering off;

                location / {
                    proxy_set_header   X-Real-IP $remote_addr;
                    proxy_set_header   X-Forwarded-Host  $host:$server_port;
                    proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header   X-Forwarded-Proto  $http_x_forwarded_proto;
                    proxy_set_header   Host $http_host;

                    proxy_connect_timeout 300;
                    proxy_http_version 1.1;
                    chunked_transfer_encoding off;
                    proxy_ignore_client_abort on;

                    proxy_pass http://http_minio;
                }
            }
        }
        ```

    * Run `docker-compose up` command

    Now, you can access the minio server via <http://127.0.0.1:9000>, requests are evenly distributed to each node.

2. [MinIO Client](https://docs.min.io/docs/minio-client-complete-guide.html)

    * Add the following content into the `service` block of preceding docker-compose.yml

        ```yaml
        mc:
            image: minio/mc
            entrypoint: ["/bin/sh"]
            depends_on:
            - nginx
            stdin_open: true
            tty: true
            volumes:
            - ./.mc:/root/.mc # mc configuration
        ```

        `stdin_open` and `tty` are for keeping this container running, because mc is just a tool and it will exit instantly, apart from these two parameters, we have to use the `entrypoint` to override the original entrypoint so that the previous parameters will take effect.

    * Run `docker exec -it <container-id> sh` to enter the mc container.

    * Run `mc config host add local http://nginx:9000 minio minio123` in the mc container, then you can use mc command to operate minion server easily.

3. [MinIO Bucket Notification](https://docs.min.io/docs/minio-bucket-notification-guide.html)

    We will try to publish MinIO events via RabbitMQ.

    * Add the following content into the `service` block of preceding docker-compose.yml

        ```yaml
        rabbitmq:
            image: rabbitmq:3.8.4-management-alpine
            environment:
            - RABBITMQ_DEFAULT_USER=admin
            - RABBITMQ_DEFAULT_PASS=admin
            ports:
            - 5672:5672
            - 15672:15672
            volumes:
            - ./rabbitmq:/var/lib/rabbitmq
        ```

    * Add the folloing content into `x-minio-common-config/environment/` of previous docker-compose.yml

        ```yaml
        MINIO_NOTIFY_AMQP_ENABLE_RABBITMQ: 'on'
        MINIO_NOTIFY_AMQP_URL_RABBITMQ: amqp://admin:admin@rabbitmq:5672
        MINIO_NOTIFY_AMQP_EXCHANGE_RABBITMQ: amq.fanout
        MINIO_NOTIFY_AMQP_EXCHANGE_TYPE_RABBITMQ: fanout
        MINIO_NOTIFY_AMQP_DURABLE_RABBITMQ: 'on'
        ```

        Restart the MinIO server and it will add the AMQP endpoint to the server. Please read the [guide](https://docs.min.io/docs/minio-bucket-notification-guide.html) carefully to understand the reasons why we set like this.

    * Create bucket notification

        You can do it by mc client or SDK, Here I will give a java sdk example, the prerequisites please refer to <https://docs.min.io/docs/java-client-quickstart-guide.html>

        ```java
        private static final String BUCKET_NAME = "bucket1";
        private static final String SQS_ARN = "arn:minio:sqs::RABBITMQ:amqp";
        .
        .
        .
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
        ```

        Notes:
        There are some issues when using the notification, you can refer to <https://github.com/minio/minio-java/issues/990> for more information.

    * Test on RabbitMQ

        Creating a new queue and binding it with the exchange (here is `amq.fanout`), When you upload a object or remove a object, the bucket notification will be sent to the exchange so the queue will also receive the event.

## Appendix

The complete docker-compose.yml:

```yaml
version: '3.7'

x-minio-common-config:
  &minio-common-config
  image: minio/minio:RELEASE.2020-06-22T03-12-50Z
  environment:
    MINIO_ACCESS_KEY: minio
    MINIO_SECRET_KEY: minio123
    MINIO_NOTIFY_AMQP_ENABLE_RABBITMQ: 'on'
    MINIO_NOTIFY_AMQP_URL_RABBITMQ: amqp://admin:admin@rabbitmq:5672
    MINIO_NOTIFY_AMQP_EXCHANGE_RABBITMQ: amq.fanout
    MINIO_NOTIFY_AMQP_EXCHANGE_TYPE_RABBITMQ: fanout
    MINIO_NOTIFY_AMQP_DURABLE_RABBITMQ: 'on'
  command: server http://minio{1...4}/data{1...2}
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
    interval: 30s
    timeout: 20s
    retries: 3

# starts 4 docker containers running minio server instances. Each
# minio server's web interface will be accessible on the host at port
# 9001 through 9004.
services:
  minio1:
    volumes:
      - data1-1:/data1
      - data1-2:/data2
    ports:
      - "9001:9000"
    << : *minio-common-config

  minio2:
    volumes:
      - data2-1:/data1
      - data2-2:/data2
    ports:
      - "9002:9000"
    << : *minio-common-config

  minio3:
    volumes:
      - data3-1:/data1
      - data3-2:/data2
    ports:
      - "9003:9000"
    << : *minio-common-config

  minio4:
    volumes:
      - data4-1:/data1
      - data4-2:/data2
    ports:
      - "9004:9000"
    << : *minio-common-config

  nginx:
    image: nginx:1.19.0
    ports:
      - "9000:9000"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf

  mc:
    image: minio/mc
    entrypoint: ["/bin/sh"]
    depends_on:
      - nginx
    stdin_open: true
    tty: true
    volumes:
      - ./.mc:/root/.mc

  rabbitmq:
    image: rabbitmq:3.8.4-management-alpine
    environment:
      - RABBITMQ_DEFAULT_USER=admin
      - RABBITMQ_DEFAULT_PASS=admin
    ports:
      - 5672:5672
      - 15672:15672
    volumes:
      - ./rabbitmq:/var/lib/rabbitmq

## By default this config uses default local driver,
## For custom volumes replace with volume driver configuration.
volumes:
  data1-1:
  data1-2:
  data2-1:
  data2-2:
  data3-1:
  data3-2:
  data4-1:
  data4-2:
```
