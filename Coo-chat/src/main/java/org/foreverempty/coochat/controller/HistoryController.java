package org.foreverempty.coochat.controller;

import org.foreverempty.common.Result;
import org.foreverempty.coochat.dto.ChatHistoryQueryDTO;
import org.foreverempty.coochat.dto.RecallRequestDTO;
import org.foreverempty.coochat.service.MessageService;
import org.foreverempty.coochat.vo.ChatHistoryCursorVO;
import org.foreverempty.coochat.vo.ChatRecentPrivateSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/group")
    public Result<ChatHistoryCursorVO> queryGroupHistory(ChatHistoryQueryDTO queryDTO) {
        return messageService.queryGroupHistory(queryDTO);
    }

    @GetMapping("/recent/private")
    public Result<List<ChatRecentPrivateSessionVO>> queryRecentPrivateSessions(
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return messageService.queryRecentPrivateSessions(limit);
    }

    @PostMapping("/private/recall")
    public Result<String> recallPrivateMessage(@RequestBody RecallRequestDTO requestDTO) {
        return messageService.recallPrivateMessage(requestDTO);
    }

    @PostMapping("/group/recall")
    public Result<String> recallGroupMessage(@RequestBody RecallRequestDTO requestDTO) {
        return messageService.recallGroupMessage(requestDTO);
    }

    @GetMapping("/group/shared/images")
    public Result<ChatHistoryCursorVO> queryGroupSharedImages(ChatHistoryQueryDTO queryDTO) {
        return messageService.queryGroupSharedImages(queryDTO);
    }

    @GetMapping("/group/shared/files")
    public Result<ChatHistoryCursorVO> queryGroupSharedFiles(ChatHistoryQueryDTO queryDTO) {
        return messageService.queryGroupSharedFiles(queryDTO);
    }
}
