package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService FollowServiceImpl;


    @PutMapping("/{followId}/{judge}")
    public Result getFollow(@PathVariable("followId") Long followId,
                            @PathVariable("judge") boolean judge){
        return FollowServiceImpl.getFollow(followId,judge);
    }

    @GetMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long id){
        return FollowServiceImpl.followOrNot(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id") Long id){
        return FollowServiceImpl.followCommon(id);
    }

}
