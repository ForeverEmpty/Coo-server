package org.foreverempty.coosocial.controller;

import org.foreverempty.common.Result;
import org.foreverempty.coosocial.service.FriendService;
import org.foreverempty.coosocial.vo.FriendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/friend")
public class FriendController {

    @Autowired
    private FriendService friendService;

    @GetMapping("/list")
    public Result<List<FriendVO>> list() {
        return friendService.getFriendList();
    }
}
