package com.example.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 明确设置JSON编解码器
        config.setCodec(new JsonJacksonCodec(objectMapper));

        config.useSingleServer()
                .setAddress(String.format("redis://%s:%d", redisHost, redisPort))
                .setDatabase(database)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(10)
                .setIdleConnectionTimeout(10000)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
