package org.foreverempty.coochat.controller;

import org.foreverempty.common.Result;
import org.foreverempty.coochat.dto.ChatHistoryQueryDTO;
import org.foreverempty.coochat.service.MessageService;
import org.foreverempty.coochat.vo.ChatHistoryCursorVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
public class HistoryController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/private")
    public Result<ChatHistoryCursorVO> queryPrivateHistory(ChatHistoryQueryDTO queryDTO) {
        return messageService.queryPrivateHistory(queryDTO);
    }
}
