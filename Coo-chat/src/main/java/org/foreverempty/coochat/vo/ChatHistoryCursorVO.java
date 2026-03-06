package org.foreverempty.coochat.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatHistoryCursorVO {
    private List<ChatHistoryMessageVO> list;
    private Boolean hasMore;
    private String nextCursor;
}
