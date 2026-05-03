package com.company.inventory.config;

import com.company.inventory.cache.DistributedLockService;
import com.company.inventory.cache.NoOpDistributedLockService;
import com.company.inventory.cache.RedisDistributedLockService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
    public RedisConnectionFactory redisConnectionFactory(RedisProperties props) {
        return new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(props.host(), props.port()));
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public DistributedLockService redisDistributedLockService(StringRedisTemplate redis) {
        return new RedisDistributedLockService(redis);
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLockService.class)
    public DistributedLockService noOpDistributedLockService() {
        return new NoOpDistributedLockService();
    }
}