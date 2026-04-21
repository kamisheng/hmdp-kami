package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private UserInfoMapper userInfoMapper;
    @Autowired
    private IUserInfoService iUserInfoService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private IUserService iUserService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result getCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);

        // session.setAttribute("code", code);
        // set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码可能会基于阿里云或其他的平台，在公司会有一个独立的服务去做，只需要调用
        log.debug("短信验证码发送成功，验证码：{}", code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        //Object code = session.getAttribute("code");
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if(!loginForm.getCode().equals(code) || code == null) {
            return Result.fail("验证码错误");
        }
        User user = iUserService.lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
        if(user == null) {
            user = new User();  //  创建新对象
            user.setPhone(loginForm.getPhone());
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            save(user);
        }
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        // 忽略一些空的值
                        .setIgnoreNullValue(true)
                        // 字段值修改器
                        .setFieldValueEditor((name,value) ->value.toString())
                );
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, stringObjectMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //session.setAttribute("user", user);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + id + format;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);
        return Result.ok();


    }

    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + id + format;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> longs = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(longs == null || longs.isEmpty()) {
            return Result.ok(0);
        }
        Long l = longs.get(1);
        if(l == null || l == 0) {
            return Result.ok(0);
        }
        int cnt = 0;
        while ((l & 1) != 0) {
            cnt++;
            l >>>= 1;
        }


        return Result.ok(cnt);
    }
}
