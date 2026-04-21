package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class ReFreshInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;
    public ReFreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //Object user = request.getSession().getAttribute("user");
        String token = request.getHeader("authorization");
        // 判空""或null
        if(StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }
        Map<Object, Object> usermap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);

        if(usermap.isEmpty()) {
            // 返回401未授权，拦截
            response.setStatus(401);
            return false;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(usermap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 刷新有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return true;
    }
}
