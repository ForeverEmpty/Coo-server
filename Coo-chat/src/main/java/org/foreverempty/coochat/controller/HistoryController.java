package org.foreverempty.coochat.controller;

import org.foreverempty.common.Result;
import org.foreverempty.coochat.dto.ChatHistoryQueryDTO;
import org.foreverempty.coochat.service.MessageService;
import org.foreverempty.coochat.vo.ChatHistoryCursorVO;
import org.foreverempty.coochat.vo.ChatRecentPrivateSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/history")
public class HistoryController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/private")
    public Result<ChatHistoryCursorVO> queryPrivateHistory(ChatHistoryQueryDTO queryDTO) {
        return messageService.queryPrivateHistory(queryDTO);
    }

    @GetMapping("/recent/private")
    public Result<List<ChatRecentPrivateSessionVO>> queryRecentPrivateSessions(
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return messageService.queryRecentPrivateSessions(limit);
    }
}
