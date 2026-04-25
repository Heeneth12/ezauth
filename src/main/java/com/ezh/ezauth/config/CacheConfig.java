package com.ezh.ezauth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("userInitCache",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build());
        manager.registerCustomCache("userMiniCache",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build());
        manager.registerCustomCache("otpCache",
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build());
        manager.registerCustomCache("pwdResetCache",
                Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build());
        return manager;
    }
}
