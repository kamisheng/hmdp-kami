package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time,  TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <F, ID> F getWithPathThrow(String name, ID id,Class<F> type, Function<ID, F> function, Long time, TimeUnit timeUnit) {
        String json =  stringRedisTemplate.opsForValue().get(name + id);
        if(StrUtil.isNotEmpty(json)){
            return BeanUtil.toBean(json, type);
        }
        if(json != null){
            return null;
        }
        F apply = function.apply(id);
        if(apply == null){
            this.set(name + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(name + id, apply, time, timeUnit);
        return apply;
    }
    public <F, ID> F queryWithMutex(String name, ID id, Object value, Class<F> type,Function<ID, F> function, Long time, TimeUnit timeUnit) {
        F f = null;
        String json = stringRedisTemplate.opsForValue().get(name + id);

        try {
            if(StrUtil.isNotEmpty(json)){
                return BeanUtil.toBean(value, type);
            }
            if(json != null){
                return null;
            }
            if(!tryLock(LOCK_SHOP_KEY + id)){
                Thread.sleep(100);
                return queryWithMutex(name, id, value, type,function, time, timeUnit);
            }
            f = function.apply(id);
            if(f == null){
                this.set(name + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            }
            stringRedisTemplate.opsForValue().set(name + id, JSONUtil.toJsonStr(f), time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(name);
        }
        return f;
    }
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    public <F, ID> F queryWithLogicalExpired(String lockName, String name, ID id, Class<F> type, Function<ID, F> function) {
        String json = stringRedisTemplate.opsForValue().get(name + id);
        if(StrUtil.isBlank(json)){
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        F f = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return f;
        }
        if(tryLock(lockName + id)){
            json = stringRedisTemplate.opsForValue().get(name + id);
            if(StrUtil.isBlank(json)){
                return null;
            }
            if(JSONUtil.toBean(json, RedisData.class).getExpireTime().isAfter(LocalDateTime.now())){
                unLock(lockName + id);
                return f;
            }
            executorService.submit(() -> {
                try {
                    F newF = function.apply(id);
                    save2Redis(name, id, 10L, newF);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockName + id);
                }
            });
            return f;
        }
        return f;
    }
    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(b);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    public <F, ID> void save2Redis(String name, ID id, Long expireSeconds, F f) {
        RedisData data =  new RedisData();
        data.setData(f);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(name +  id, JSONUtil.toJsonStr(data));
    }

}
