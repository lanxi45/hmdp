package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到Session
        session.setAttribute("code", code);
        //5.发送验证码
        log.debug("发送短信验证码成功，验证码:{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //校验验证码
        Object cacheCode = session.getAttribute("code");//获取后端生成的验证码
        String code = loginForm.getCode();//获取前端输入的验证码
        //这里的toStriong()是把cacheCode转为String类型
        if(cacheCode == null || !cacheCode.toString().equals(code)){
            //3.不一致，返回错误信息
            return Result.fail("验证码错误！");
        }

        //4.一致，根据手机号查询用户 select * from user where phone = ?
        User user = query().eq("phone", phone).one();//这里使用了mybatisplus

        //5.判断用户是否存在
        if(user == null){
            //6.不存在，创建新用户并保存
            user = createUserWhthPhone(phone);//用户，都要保存到Session里
        }
        
        //7
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //session的原理是cookie，每一个Session都有一个唯一的SessionIDz，
        // 在访问Tomcat的时候SessionID就已经保存到cookie里面
        //以后的请求就好带着SessionID，就能够找到Session，就能够找到用户
        //所以这里不需要返回登录凭证。
        return Result.ok();
    }

    private User createUserWhthPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }


}
