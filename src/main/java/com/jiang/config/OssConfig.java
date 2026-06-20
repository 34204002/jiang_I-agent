package com.jiang.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 对象存储配置，注入 {@code spring.oss.*} 属性并创建 OSS Client Bean。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.oss")
public class OssConfig {

    /** OSS 地域节点（如 oss-cn-beijing.aliyuncs.com） */
    private String endpoint;
    /** RAM 用户 AccessKey ID */
    private String accessKeyId;
    /** RAM 用户 AccessKey Secret */
    private String accessKeySecret;
    /** Bucket 名称 */
    private String bucketName;

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
