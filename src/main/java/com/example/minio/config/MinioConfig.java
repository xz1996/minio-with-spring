package com.example.minio.config;

import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Jerry Xie
 * @date 6/24/2020 15:37
 */
@Configuration
public class MinioConfig {

  @Bean
  public MinioClient minioClient(MinioProperties minioProperties)
      throws InvalidPortException, InvalidEndpointException {
    return new MinioClient(
        minioProperties.endpoint, minioProperties.accessKeyId, minioProperties.accessKeySecret);
  }

  @Bean
  @ConfigurationProperties("minio")
  public MinioProperties minioProperties() {
    return new MinioProperties();
  }

  @Setter
  @Getter
  public static class MinioProperties {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
  }
}
